package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Slf4j
public class ShopController {
    @Autowired
    private RedisTemplate redisTemplate;

    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @GetMapping("/status")
    public Result<Integer> getStatus() {
        log.info("查询店铺营业状态...");
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        //Redis中无记录（如Redis重启）时按打烊处理，避免返回null
        return Result.success(status == null ? StatusConstant.DISABLE : status);
    }
}
