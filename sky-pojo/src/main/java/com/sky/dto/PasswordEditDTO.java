package com.sky.dto;

import com.sky.constant.MessageConstant;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class PasswordEditDTO implements Serializable {

    //员工id
    private Long empId;

    //旧密码
    @NotBlank(message = MessageConstant.PASSWORD_IS_NULL)
    private String oldPassword;

    //新密码
    @NotBlank(message = MessageConstant.NEW_PASSWORD_IS_NULL)
    private String newPassword;

}
