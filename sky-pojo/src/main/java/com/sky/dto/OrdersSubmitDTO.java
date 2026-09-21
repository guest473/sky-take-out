package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sky.constant.MessageConstant;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrdersSubmitDTO implements Serializable {
    //地址簿id
    @NotNull(message = MessageConstant.ADDRESS_BOOK_IS_NULL)
    private Long addressBookId;
    //付款方式：1微信支付，未传时由服务端按默认值兜底
    private Integer payMethod;
    //备注
    private String remark;
    //预计送达时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedDeliveryTime;
    //配送状态  1立即送出  0选择具体时间
    private Integer deliveryStatus;
    //餐具数量
    private Integer tablewareNumber;
    //餐具数量状态  1按餐量提供  0选择具体数量
    private Integer tablewareStatus;
    //打包费
    private Integer packAmount;
    //总金额
    private BigDecimal amount;
}
