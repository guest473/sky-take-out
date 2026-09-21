package com.sky.dto;

import com.sky.constant.MessageConstant;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class OrdersPaymentDTO implements Serializable {

    //订单号
    @NotBlank(message = MessageConstant.ORDER_NUMBER_IS_NULL)
    private String orderNumber;

    //付款方式
    private Integer payMethod;

}
