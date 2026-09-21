package com.sky.task;

import com.sky.service.MyTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

//定时任务入口：只负责按cron触发，具体逻辑在 MyTaskService
@Component
@Slf4j
public class MyTaskScheduler {
    @Autowired
    private MyTaskService myTaskService;

    @Scheduled(cron = "0 0/2 * * * ?")
    public void unpayAutoCancel() {
        log.info("订单超时自动取消");
        myTaskService.unpayAutoCancel();
    }

    @Scheduled(cron = "0 0 0/1 * * ?")
    public void longTimeUnreachAutoReach() {
        log.info("长时间送达自动完成");
        myTaskService.longTimeUnreachAutoReach();
    }
}
