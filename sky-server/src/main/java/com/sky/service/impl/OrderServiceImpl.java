package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.websocket.WebSocketServer;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;


@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    //订单号自增序号，与毫秒时间戳组合使用
    private static final AtomicLong ORDER_NUMBER_SEQ = new AtomicLong();

    //同一用户下单的防重复提交短时锁
    private static final String ORDER_SUBMIT_KEY_PREFIX = "order:submit:";
    private static final Duration ORDER_SUBMIT_LOCK = Duration.ofSeconds(10);

    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private RedisTemplate redisTemplate;

    //商家接单
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        //条件更新：仅当订单仍处于待接单时才改状态，避免与拒单/取消互相覆盖
        int rows = orderMapper.updateStatusIfMatch(ordersConfirmDTO.getId(), Orders.TO_BE_CONFIRMED, Orders.CONFIRMED);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        notifyOrderStatusChanged(ordersConfirmDTO.getId());
    }
    //商家派单
    @Override
    public void assign(Long id) {
        //条件更新：仅当订单仍处于已接单状态时才派单
        int rows = orderMapper.updateStatusIfMatch(id, Orders.CONFIRMED, Orders.DELIVERY_IN_PROGRESS);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        notifyOrderStatusChanged(id);
    }
    //商家订单完成
    @Override
    public void complete(Long id){
        //条件更新：仅当订单仍处于派送中状态时才完成
        int rows = orderMapper.updateStatusIfMatch(id, Orders.DELIVERY_IN_PROGRESS, Orders.COMPLETED);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        notifyOrderStatusChanged(id);
    }

    //商家拒单
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        //真实支付场景下，这里还需对已支付订单调用退款（当前为模拟支付，未接入）
        //条件更新：仅当订单仍处于待接单时才写入拒单原因，避免与接单/取消互相覆盖
        int rows = orderMapper.rejectIfMatch(ordersRejectionDTO.getId(), Orders.TO_BE_CONFIRMED,
                Orders.CANCELLED, ordersRejectionDTO.getRejectionReason(), LocalDateTime.now());
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        notifyOrderStatusChanged(ordersRejectionDTO.getId());
    }
    //商家取消订单
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO){
        //真实支付场景下，这里还需对已支付订单调用退款（当前为模拟支付，未接入）
        //条件更新：仅当订单仍处于未完成且未取消的状态时才取消
        //原来只排除"已完成"，导致对已取消的订单重复取消会覆盖 cancel_reason / cancel_time
        int rows = orderMapper.cancelIfStatusIn(ordersCancelDTO.getId(),
                Arrays.asList(Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED,
                        Orders.CONFIRMED, Orders.DELIVERY_IN_PROGRESS),
                Orders.CANCELLED, ordersCancelDTO.getCancelReason(), LocalDateTime.now());
        if (rows == 0) {
            throw new OrderBusinessException("订单不存在、已完成或已取消，不能取消");
        }
        notifyOrderStatusChanged(ordersCancelDTO.getId());
    }


    //商家查询订单详情
    @Override
    public OrderVO details(Long id) {
        Orders orders =orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        List<OrderDetail> list=orderMapper.getOrderDetailByOrderId(id);
        if(list != null && !list.isEmpty()){
            orderVO.setOrderDetailList(list);
        }
        return orderVO;
    }

    //商家分页、条件查询订单(包括菜品信息）
    @Override
    public PageResult page(OrdersPageQueryDTO ordersPageQueryDTO) {
        //分页参数兜底：未传或非法时按第1页、每页10条
        int pageNum = ordersPageQueryDTO.getPage() < 1 ? 1 : ordersPageQueryDTO.getPage();
        int pageSize = ordersPageQueryDTO.getPageSize() < 1 ? 10 : ordersPageQueryDTO.getPageSize();
        PageHelper.startPage(pageNum, pageSize);
        Page<OrderVO> page = orderMapper.page(ordersPageQueryDTO);
        List<OrderVO> list = page.getResult();
        if(list != null && !list.isEmpty()){
            //一次性取回当前页所有订单的菜品数据并按订单分组，避免逐单查询
            Map<Long, List<OrderDetail>> detailMap = getOrderDetailMap(
                    list.stream().map(OrderVO::getId).collect(Collectors.toList()));
            for (OrderVO orderVO : list) {
                String orderDishes = detailMap.getOrDefault(orderVO.getId(), new ArrayList<>())
                        .stream()
                        .map(x -> x.getName() + "*" + x.getNumber())
                        .collect(Collectors.joining(","));
                orderVO.setOrderDishes(orderDishes);
            }
        }
        PageResult pageResult = new PageResult();
        pageResult.setTotal(page.getTotal());
        pageResult.setRecords(list);
        return pageResult;
    }
    //商家统计
    @Override
    public OrderStatisticsVO statistics() {
       OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
       orderStatisticsVO.setConfirmed(orderMapper.countStatus(Orders.CONFIRMED));
       orderStatisticsVO.setDeliveryInProgress(orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS));
       orderStatisticsVO.setToBeConfirmed(orderMapper.countStatus(Orders.TO_BE_CONFIRMED));
        return orderStatisticsVO;
    }

    //用户下单
    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();
        //短时锁：同一用户并发重复提交只放行一次，事务结束（提交或回滚）后才释放
        String submitKey = ORDER_SUBMIT_KEY_PREFIX + userId;
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(submitKey, "1", ORDER_SUBMIT_LOCK))) {
            throw new OrderBusinessException(MessageConstant.ORDER_REPEAT_SUBMIT);
        }
        //在事务提交/回滚后释放锁：若在方法内 finally 释放，释放会早于事务提交，
        //窗口内同一用户的第二次请求会读到尚未清空的购物车，导致重复下单
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                redisTemplate.delete(submitKey);
            }
        });
        return doSubmit(ordersSubmitDTO, userId);
    }

    //下单主体
    private OrderSubmitVO doSubmit(OrdersSubmitDTO ordersSubmitDTO, Long userId) {
    //校验收货地址存在且属于当前登录用户，避免用他人地址下单后读到他人收货信息
        AddressBook addressBook = addressBookMapper.getByIdAndUserId(ordersSubmitDTO.getAddressBookId(), userId);
        if(addressBook == null)
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        //校验购物车不为空
        ShoppingCart shoppingCart =new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list=shoppingCartMapper.getById(shoppingCart);
        if(list.isEmpty())
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        //购物车中的商品可能在下单前被商家停售或所属分类被停用，落单前逐项确认仍可售
        List<String> notSellableNames = shoppingCartMapper.listNotSellableNames(userId);
        if (!notSellableNames.isEmpty()) {
            log.info("购物车中存在已下架商品，拒绝下单：{}", notSellableNames);
            throw new OrderBusinessException(
                    String.format(MessageConstant.GOODS_NOT_SELLABLE, notSellableNames.get(0)));
        }
    //插入订单
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        //前端未传时按数据库默认值兜底，避免写入NULL导致下单失败
        if (orders.getPayMethod() == null) {
            orders.setPayMethod(Orders.PAY_METHOD_WECHAT);
        }
        if (orders.getDeliveryStatus() == null) {
            orders.setDeliveryStatus(Orders.DELIVERY_IMMEDIATE);
        }
        if (orders.getTablewareStatus() == null) {
            orders.setTablewareStatus(Orders.TABLEWARE_BY_MEAL);
        }
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setNumber(generateOrderNumber());
        //支付状态
        orders.setPayStatus(Orders.UN_PAID);
        //订单状态
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setConsignee(addressBook.getConsignee());
        orders.setPhone(addressBook.getPhone());
        orders.setAddress(addressBook.getDetail());
        //订单金额以服务端购物车数据为准，不采用客户端传入的金额
        orders.setAmount(calcAmount(list));
        orderMapper.insert(orders);
    //插入订单明细
        //主键回显
        Long orderId = orders.getId();
        List<OrderDetail> orderDetailList = new  ArrayList<>();
        for (ShoppingCart cart: list) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setId(null);
            orderDetail.setOrderId(orderId);
            orderDetailList.add(orderDetail);
        }
        orderMapper.insertOrderDetail(orderDetailList);
    //清空购物车
    shoppingCartMapper.clean(userId);
    //返回订单数据
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
        return orderSubmitVO;
    }

    //订单号：毫秒时间戳 + 3位进程内自增序号，避免同一毫秒内并发下单生成重复订单号
    private String generateOrderNumber() {
        return System.currentTimeMillis() + String.format("%03d", ORDER_NUMBER_SEQ.incrementAndGet() % 1000);
    }

    //按购物车明细（单价 × 份数）汇总订单金额
    private BigDecimal calcAmount(List<ShoppingCart> shoppingCartList) {
        BigDecimal amount = BigDecimal.ZERO;
        for (ShoppingCart cart : shoppingCartList) {
            amount = amount.add(cart.getAmount().multiply(BigDecimal.valueOf(cart.getNumber())));
        }
        return amount;
    }

    //查询订单并校验其属于当前登录用户，避免横向越权
    private Orders getOwnOrder(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null || !Objects.equals(orders.getUserId(), BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return orders;
    }


    //用户订单支付
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        //校验订单存在且属于当前登录用户，避免用他人订单号发起支付
        Orders ordersDB = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (ordersDB == null || !Objects.equals(ordersDB.getUserId(), BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //仅待支付订单可支付，避免用已取消/已完成的订单号重放把订单改回待接单
        if (!Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //当前为模拟支付：直接返回空的预支付参数，订单状态由 paySuccess 修改
        //接入真实支付时改为调用 WeChatPayUtil.pay(订单号, 金额, 描述, openid)
        JSONObject jsonObject = new JSONObject();

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    //用户支付成功，修改订单状态
    @Override
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 条件更新：仅待支付订单才置为已支付，重复回调或重放不会重复改单、重复推送来单提醒
        int rows = orderMapper.payIfPending(ordersDB.getId(), Orders.PENDING_PAYMENT,
                Orders.TO_BE_CONFIRMED, Orders.PAID, LocalDateTime.now());
        if (rows == 0) {
            log.info("订单当前状态不可支付，跳过改单与提醒，订单号：{}", outTradeNo);
            return;
        }

        //来单提醒（type=1）
        sendOrderMessage(1, ordersDB.getId(), "订单号" + outTradeNo);
    }
    //历史订单查询
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        // 设置分页（分页参数未传或非法时按第1页、每页10条兜底）
        pageNum = pageNum < 1 ? 1 : pageNum;
        pageSize = pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList();

        // 一次性取回当前页所有订单的明细并按订单分组，再封装入OrderVO响应
        if (page != null && page.getTotal() > 0) {
            Map<Long, List<OrderDetail>> detailMap = getOrderDetailMap(
                    page.getResult().stream().map(Orders::getId).collect(Collectors.toList()));
            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(detailMap.getOrDefault(orders.getId(), new ArrayList<>()));

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    //批量查询订单明细并按订单id分组，供订单列表装配使用
    private Map<Long, List<OrderDetail>> getOrderDetailMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new HashMap<>();
        }
        return orderMapper.getOrderDetailByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));
    }

    @Override
    public OrderVO getOrderDetailById(Long id) {
        Orders orders = getOwnOrder(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderMapper.getOrderDetailByOrderId(id));
        return orderVO;
    }

    @Override
    public void userCancel(Long id) {
        Orders orders = getOwnOrder(id);
        Integer status = orders.getStatus();
        //先按读到的状态给出不可取消的具体原因（仅用于提示，真正的拦截靠下面的条件更新）
        if (Orders.DELIVERY_IN_PROGRESS.equals(status)) {
            throw new OrderBusinessException("订单已派送，无法取消，请致电商家");
        }
        if (Orders.COMPLETED.equals(status)) {
            throw new OrderBusinessException("订单已完成，不可取消");
        }
        if (Orders.CANCELLED.equals(status)) {
            throw new OrderBusinessException("订单已取消，请勿重复操作");
        }
        //真实支付场景下已支付订单取消需调 WeChatPayUtil.refund(...) 退款，当前为模拟支付无实际资金流
        //条件更新：仅当订单仍处于待付款/待接单/已接单时才取消
        //读状态到这里之间商家可能已接单并派送，此时状态已变、语句不会命中，避免覆盖商家的流转
        int rows = orderMapper.cancelIfStatusIn(id,
                Arrays.asList(Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED, Orders.CONFIRMED),
                Orders.CANCELLED, "用户取消", LocalDateTime.now());
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    @Override
    @Transactional
    public void repetition(Long id) {
        //校验订单归属，避免把他人订单的菜品加入自己的购物车
        getOwnOrder(id);

        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderMapper.getOrderDetailByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        //用 insertOrIncrement（ON DUPLICATE KEY UPDATE）而不是普通 insert：
        //购物车里已有同款菜品时会撞 uk_cart_user_item 唯一索引，普通 insert 直接报错，
        //用户看到的是"已存在"，且已插入的几条无法回滚（该方法原先没有事务）
        for (ShoppingCart cart : shoppingCartList) {
            shoppingCartMapper.insertOrIncrement(cart);
        }
    }

    @Override
    public void reminder(Long orderId) {
        Orders orders = getOwnOrder(orderId);
        //催单提醒（type=2）
        sendOrderMessage(2, orderId, "订单号" + orders.getNumber());
    }

    //订单状态发生变化后通知所有管理端刷新（type=3，前端只刷新不弹提示）
    //这样任一终端接单/派单/完成/拒单/取消后，其他终端的订单列表与概览会同步更新
    private void notifyOrderStatusChanged(Long orderId) {
        sendOrderMessage(3, orderId, "订单状态已更新");
    }

    //向所有管理端推送订单消息：type 1 来单、2 催单、3 状态变更
    private void sendOrderMessage(int type, Long orderId, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("orderId", orderId);
        map.put("content", content);
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }
}