package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeLoginVO implements Serializable {

    private Long id;

    private String userName;

    private String name;

    private String token;

    //角色：1店长 0店员，前端据此决定菜单与按钮
    private Integer role;

}
