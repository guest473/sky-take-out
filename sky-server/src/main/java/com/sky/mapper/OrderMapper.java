package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    //插入订单数据
    void insert(Orders orders);
    //插入订单明细数据
    void insertOrderDetail(List<OrderDetail> orderDetailList);

    //分页、条件查询订单
    //商家
    Page<OrderVO> page(OrdersPageQueryDTO ordersPageQueryDTO);
    //用户
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);


    //统计
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer confirmed);

    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    //根据订单号查询订单
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    //根据订单号查询订单详情（具体菜品）
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getOrderDetailByOrderId(Long orderId);

    //批量查询订单明细（订单分页时一次性取回，避免逐单查询）
    List<OrderDetail> getOrderDetailByOrderIds(@Param("orderIds") List<Long> orderIds);

    //条件更新订单状态：仅当订单仍处于 fromStatus 时才更新，返回受影响行数
    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("fromStatus") Integer fromStatus,
                            @Param("toStatus") Integer toStatus);

    //条件取消未支付订单：仅当订单仍为未支付且状态未变时才取消，返回受影响行数
    int cancelIfUnpaid(@Param("id") Long id,
                       @Param("fromStatus") Integer fromStatus,
                       @Param("toStatus") Integer toStatus,
                       @Param("payStatus") Integer payStatus,
                       @Param("cancelReason") String cancelReason,
                       @Param("cancelTime") LocalDateTime cancelTime);

    //支付成功：仅当订单仍处于待支付状态时才置为已支付，返回受影响行数（保证重复回调幂等）
    int payIfPending(@Param("id") Long id,
                     @Param("fromStatus") Integer fromStatus,
                     @Param("toStatus") Integer toStatus,
                     @Param("payStatus") Integer payStatus,
                     @Param("checkoutTime") LocalDateTime checkoutTime);

    //商家拒单：仅当订单仍处于 fromStatus 时才写入拒单原因，返回受影响行数
    int rejectIfMatch(@Param("id") Long id,
                      @Param("fromStatus") Integer fromStatus,
                      @Param("toStatus") Integer toStatus,
                      @Param("rejectionReason") String rejectionReason,
                      @Param("cancelTime") LocalDateTime cancelTime);

    //用户取消：仅当订单仍处于 fromStatuses 之一时才取消，返回受影响行数
    int cancelIfStatusIn(@Param("id") Long id,
                         @Param("fromStatuses") List<Integer> fromStatuses,
                         @Param("toStatus") Integer toStatus,
                         @Param("cancelReason") String cancelReason,
                         @Param("cancelTime") LocalDateTime cancelTime);

    //有效订单数量
    Integer countByMap(Map map);
    //营业额
    Double sumByMap(Map map);

    //按日聚合订单数：一次取回整个区间，避免报表逐日查询；status 为 null 时统计全部订单
    List<Map<String, Object>> countByDay(@Param("begin") LocalDateTime begin,
                                        @Param("end") LocalDateTime end,
                                        @Param("status") Integer status);
    //按日聚合营业额：一次取回整个区间，避免报表逐日查询
    List<Map<String, Object>> sumByDay(@Param("begin") LocalDateTime begin,
                                       @Param("end") LocalDateTime end,
                                       @Param("status") Integer status);
    //销量前10
    List<GoodsSalesDTO> getSalesTop10(LocalDateTime begin, LocalDateTime end);

    //订单状态、支付状态、下单时间来查询订单（订单超时自动取消）
    //带上 status 条件，避免把已取消的历史订单一直捞出来反复扫描
    @Select("select * from orders where status = #{status} and pay_status = #{payStatus} and order_time < #{time}")
    List<Orders> getByStatusAndPayStatus(@Param("status") Integer status,
                                         @Param("payStatus") Integer payStatus,
                                         @Param("time") LocalDateTime time);
    //订单状态、下单时间来查询订单（长时间送达自动完成）
    @Select("select * from orders where status = #{status} and order_time < #{time}")
    List<Orders> getOrderByIdAndStatus(Integer status, LocalDateTime time);
}
