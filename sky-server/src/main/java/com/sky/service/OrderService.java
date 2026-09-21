package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {
   //商家
     //商家接单
     void confirm(OrdersConfirmDTO ordersConfirmDTO);
     //商家派单
     void assign(Long id);
     //商家完成订单
     void complete(Long id);
     //商家拒单
     void rejection(OrdersRejectionDTO ordersRejectionDTO);
     //商家取消订单
     void cancel(OrdersCancelDTO ordersCancelDTO);
     //查询订单详情
     OrderVO details(Long id);
     //商家分页、条件查询订单(包括菜品信息）
     PageResult page(OrdersPageQueryDTO ordersPageQueryDTO);
     //商家统计
     OrderStatisticsVO statistics();
   //用户
     //用户下单
     OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO);
     //用户支付
     OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;
     //用户支付成功
     void paySuccess(String outTradeNo);
     //历史订单查询
     PageResult pageQuery4User(int page, int pageSize, Integer status);
     //用户查订单信息
     OrderVO getOrderDetailById(Long id);
     //用户取消订单
     void userCancel(Long id);
     //用户再来一单
     void repetition(Long id);

     //催单
     void reminder(Long id);
}
