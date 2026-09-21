package com.sky.dto;

import com.sky.constant.MessageConstant;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class EmployeeLoginDTO implements Serializable {

    @NotBlank(message = MessageConstant.EMPLOYEE_USERNAME_IS_NULL)
    private String username;

    @NotBlank(message = MessageConstant.PASSWORD_IS_NULL)
    private String password;

}
