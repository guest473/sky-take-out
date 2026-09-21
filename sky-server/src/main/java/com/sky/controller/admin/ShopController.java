package com.sky.controller.admin;

import com.sky.constant.StatusConstant;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Slf4j
public class ShopController {
    @Autowired
    private RedisTemplate redisTemplate;

    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";
    //营业状态保留时长，避免key永久驻留
    private static final Duration SHOP_STATUS_TTL = Duration.ofDays(30);

    @PutMapping("/{status}")
    public Result<String> setStatus(@PathVariable Integer status) {
        log.info("设置店铺营业状态:{}", status == 1 ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(SHOP_STATUS_KEY, status, SHOP_STATUS_TTL);
        return Result.success();
    }

    @GetMapping("/status")
    public Result<Integer> getStatus() {
        log.info("查询店铺营业状态...");
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        //Redis中无记录（如Redis重启）时按打烊处理，避免返回null
        return Result.success(status == null ? StatusConstant.DISABLE : status);
    }
}
