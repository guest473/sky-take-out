package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.RoleConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

//员工管理
@RestController
@RequestMapping("/admin/employee")
@Slf4j
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    //登录
    @PostMapping("/login")
    public Result<EmployeeLoginVO> login(@Valid @RequestBody EmployeeLoginDTO employeeLoginDTO) {
        //只记录用户名，DTO 中含明文密码，整体打印会把口令写进日志
        log.info("员工登录：{}", employeeLoginDTO.getUsername());

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令牌
        Integer role = employee.getRole() == null ? RoleConstant.STAFF : employee.getRole();
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        claims.put(JwtClaimsConstant.EMP_ROLE, role);
        //带上签发时的权限版本，之后该员工角色/启用状态/密码变化时旧令牌会被拦截器判为失效
        claims.put(JwtClaimsConstant.EMP_AUTH_VERSION, employeeService.getAuthVersion(employee.getId()));
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .role(role)
                .build();

        return Result.success(employeeLoginVO);
    }

    //退出
    @PostMapping("/logout")
    public Result<String> logout() {
        //递增当前员工的权限版本，让该令牌立刻失效，而不是等它自然过期
        employeeService.logout();
        return Result.success();
    }

    //新增员工
    @PostMapping
    public Result save(@Valid @RequestBody EmployeeDTO employeeDTO){
        //只记录账号：DTO 里含店长设置的初始密码，不能整体打印
        log.info("新增员工：{}",employeeDTO.getUsername());
        employeeService.save(employeeDTO);
        return Result.success();
    }

    //员工分页查询

    @GetMapping("/page")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO){
        log.info("员工分页查询，参数为：{}", employeePageQueryDTO);
        PageResult pageResult = employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    //启用禁用员工账号
    @PostMapping("/status/{status}")
    public Result startOrStop(@PathVariable Integer status,Long id){
        log.info("启用禁用员工账号：{},{}",status,id);
        employeeService.startOrStop(status,id);
        return Result.success();
    }

    //根据id查询员工信息
    @GetMapping("/{id}")
    public Result<Employee> getById(@PathVariable Long id){
        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }

    //编辑员工信息
    @PutMapping
    public Result update(@Valid @RequestBody EmployeeDTO employeeDTO){
        //只记录id：DTO 里可能带 password 字段，不做整体打印
        log.info("编辑员工信息：{}", employeeDTO.getId());
        employeeService.update(employeeDTO);
        return Result.success();
    }

    //修改当前登录员工密码
    @PutMapping("/editPassword")
    public Result<String> editPassword(@Valid @RequestBody PasswordEditDTO passwordEditDTO) {
        log.info("修改密码：{}", passwordEditDTO.getEmpId());
        employeeService.editPassword(passwordEditDTO);
        return Result.success();
    }
}
