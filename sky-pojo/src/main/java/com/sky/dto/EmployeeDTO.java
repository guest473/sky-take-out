package com.sky.dto;

import com.sky.constant.MessageConstant;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class EmployeeDTO implements Serializable {

    private Long id;

    @NotBlank(message = MessageConstant.EMPLOYEE_USERNAME_IS_NULL)
    private String username;

    @NotBlank(message = MessageConstant.EMPLOYEE_NAME_IS_NULL)
    private String name;

    @NotBlank(message = MessageConstant.PHONE_IS_NULL)
    private String phone;

    @NotBlank(message = MessageConstant.EMPLOYEE_SEX_IS_NULL)
    private String sex;

    @NotBlank(message = MessageConstant.EMPLOYEE_ID_NUMBER_IS_NULL)
    private String idNumber;

    //角色：1店长 0店员
    private Integer role;

    //初始密码：仅新增员工时使用；编辑员工不改密码（改密码走 editPassword 接口），故不参与校验
    private String password;

}
