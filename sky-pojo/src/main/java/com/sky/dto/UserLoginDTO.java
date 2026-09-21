package com.sky.dto;

import com.sky.constant.MessageConstant;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

//C端用户登录
@Data
public class UserLoginDTO implements Serializable {

    @NotBlank(message = MessageConstant.WECHAT_CODE_IS_NULL)
    private String code;

}
