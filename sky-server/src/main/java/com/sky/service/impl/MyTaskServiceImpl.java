package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.MyTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class MyTaskServiceImpl implements MyTaskService {

    //任务锁的key前缀：多实例部署时，同一时刻只让一个实例执行同一个定时任务
    private static final String TASK_LOCK_KEY_PREFIX = "task:lock:";
    //锁的持有者标识，释放时校验，避免删掉其他实例的锁
    private static final String LOCK_HOLDER = UUID.randomUUID().toString();
    //超时取消任务每2分钟触发一次：TTL 要小于调度间隔，实例意外退出后下一轮能重新抢到锁
    private static final Duration UNPAY_CANCEL_LOCK_TTL = Duration.ofSeconds(90);
    //自动完成任务每小时触发一次：TTL 只需大于任务本身的耗时
    private static final Duration LONG_TIME_UNREACH_LOCK_TTL = Duration.ofMinutes(5);

    //释放锁的Lua脚本：比对持有者与传入标识一致才删除，比对与删除在Redis内一次完成
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    //订单超时自动取消
    @Override
    public void unpayAutoCancel() {
        String lockKey = TASK_LOCK_KEY_PREFIX + "unpayAutoCancel";
        if (!tryLock(lockKey, UNPAY_CANCEL_LOCK_TTL)) {
            return;
        }
        try {
            LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
            List<Orders> unpayList = orderMapper.getByStatusAndPayStatus(Orders.PENDING_PAYMENT, Orders.UN_PAID, time);
            if (unpayList == null || unpayList.isEmpty()) {
                return;
            }
            for (Orders orders : unpayList) {
                //条件更新：若这期间用户已完成支付，pay_status 已变，语句不会命中，避免误取消已支付订单
                int rows = orderMapper.cancelIfUnpaid(orders.getId(), Orders.PENDING_PAYMENT, Orders.CANCELLED,
                        Orders.UN_PAID, "订单超时未支付", LocalDateTime.now());
                if (rows == 0) {
                    log.info("订单{}状态已变更，跳过自动取消", orders.getId());
                }
            }
        } finally {
            releaseLock(lockKey);
        }
    }

    //订单长时间处于配送中，自动完成
    @Override
    public void longTimeUnreachAutoReach() {
        String lockKey = TASK_LOCK_KEY_PREFIX + "longTimeUnreachAutoReach";
        if (!tryLock(lockKey, LONG_TIME_UNREACH_LOCK_TTL)) {
            return;
        }
        try {
            LocalDateTime time = LocalDateTime.now().plusHours(-3);
            List<Orders> unReachList = orderMapper.getOrderByIdAndStatus(Orders.DELIVERY_IN_PROGRESS, time);
            if (unReachList == null || unReachList.isEmpty()) {
                return;
            }
            for (Orders orders : unReachList) {
                //条件更新：仅当订单仍处于配送中时才置为已完成
                orderMapper.updateStatusIfMatch(orders.getId(), Orders.DELIVERY_IN_PROGRESS, Orders.COMPLETED);
            }
        } finally {
            releaseLock(lockKey);
        }
    }

    //抢占任务锁：key 不存在时写入成功并返回 true，已被其他实例持有时返回 false
    private boolean tryLock(String lockKey, Duration ttl) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, LOCK_HOLDER, ttl);
        if (!Boolean.TRUE.equals(success)) {
            log.debug("未抢到任务锁，跳过本次执行：{}", lockKey);
            return false;
        }
        return true;
    }

    //释放任务锁：只删自己的锁。先get再delete不是原子操作，
    //两步之间锁若过期，其他实例可能刚好抢到锁并被这里误删，故改为Lua脚本一次完成
    private void releaseLock(String lockKey) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), LOCK_HOLDER);
    }
}
