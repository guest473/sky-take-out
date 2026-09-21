package com.sky.service;

import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.result.PageResult;

public interface EmployeeService {

    //员工登录
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    //新增员工
    void save(EmployeeDTO employeeDTO);

    //分页查询
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    //启用禁用员工账号
    void startOrStop(Integer status, Long id);

    //根据id查询员工
    Employee getById(Long id);

    //编辑员工信息
    void update(EmployeeDTO employeeDTO);

    //修改当前员工密码
    void editPassword(PasswordEditDTO passwordEditDTO);

    //查询员工当前的权限版本（角色、启用状态或密码变化时会递增，用于让该员工的旧令牌立即失效）
    int getAuthVersion(Long empId);

    //退出登录：递增当前员工的权限版本，使其已签发的令牌立即失效
    void logout();
}
