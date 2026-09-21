package com.sky.controller.admin;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("adminOrderController")
@RequestMapping("/admin/order")
@Slf4j
public class OrderController {
    @Autowired
    private OrderService orderService;
    //商家接单
    @PutMapping("/confirm")
    public Result confirm(@RequestBody OrdersConfirmDTO  ordersConfirmDTO) {
        log.info("接单：{}", ordersConfirmDTO);
        orderService.confirm(ordersConfirmDTO);
        return Result.success();
    }
    //派单
    @PutMapping("/delivery/{id}")
    public Result assign(@PathVariable Long id) {
        log.info("派单：{}", id);
        orderService.assign(id);
        return Result.success();
    }
    //订单完成
    @PutMapping("complete/{id}")
    public Result complete(@PathVariable Long id) {
        log.info("完成订单：{}", id);
        orderService.complete(id);
        return Result.success();
    }
    //商家拒单
    @PutMapping("/rejection")
    public Result rejection(@RequestBody OrdersRejectionDTO ordersRejectionDTO)  {
        log.info("拒单：{}", ordersRejectionDTO);
        orderService.rejection(ordersRejectionDTO);
        return Result.success();
    }
    //商家取消订单
    @PutMapping("/cancel")
    public Result cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) {
        log.info("商家取消订单：{}", ordersCancelDTO);
        orderService.cancel(ordersCancelDTO);
        return Result.success();
    }
    //查询订单详情
    @GetMapping("/details/{id}")
    public Result<OrderVO> details(@PathVariable Long id) {
        log.info("查询订单详情：{}", id);
        return Result.success(orderService.details(id));
    }
    //分页、条件查询订单(包括菜品信息）
    @GetMapping("/conditionSearch")
    public Result<PageResult> page(OrdersPageQueryDTO ordersPageQueryDTO) {
        //不整体打印该DTO：其中的 phone 是搜索用的手机号
        log.info("订单分页查询：page={}, pageSize={}, status={}",
                ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize(), ordersPageQueryDTO.getStatus());
        PageResult pageResult= orderService.page(ordersPageQueryDTO);
        return Result.success(pageResult);
    }
    //统计
    @GetMapping("/statistics")
    public Result statistics() {
        log.info("统计");
        OrderStatisticsVO orderStatisticsVO = orderService.statistics();
        return Result.success(orderStatisticsVO);
    }

}

