package com.sky.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.MessageConstant;
import com.sky.constant.RoleConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

//jwt令牌校验的拦截器
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    //仅店长角色可访问的接口前缀
    private static final List<String> MANAGER_ONLY_PATH_PREFIXES = Arrays.asList(
            "/admin/employee", "/admin/report", "/admin/shop");

    //虽在前缀范围内但对所有已登录员工开放的接口
    //概览页对所有角色开放，其中的营业状态查询不能只给店长（切换营业状态仍是店长专属）
    private static final List<String> ALL_EMPLOYEE_PATHS = Arrays.asList(
            "/admin/employee/logout", "/admin/employee/editPassword", "/admin/shop/status");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private EmployeeService employeeService;

    //校验jwt

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getAdminTokenName());

        //2、校验令牌
        try {
            //不打印令牌明文，避免日志泄露
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            log.info("当前员工id:{}", empId);
            BaseContext.setCurrentId(empId);

            //3、令牌里的权限版本必须与该员工当前的版本一致
            //员工被改角色、被启用/禁用、改过密码后版本会递增，此时旧令牌立即失效，要求重新登录
            if (tokenAuthVersion(claims) != employeeService.getAuthVersion(empId)) {
                log.info("员工{}的令牌已失效（权限版本已变化），要求重新登录", empId);
                BaseContext.removeCurrentId();
                response.setStatus(401);
                return false;
            }

            //4、粗粒度角色校验：员工管理、数据报表、营业状态仅店长可访问
            String uri = request.getRequestURI();
            if (isManagerOnlyPath(uri) && !isManager(claims)) {
                log.info("员工{}无权限访问：{}", empId, uri);
                //提前返回时afterCompletion不会执行，这里显式清理
                BaseContext.removeCurrentId();
                writeNoPermission(response);
                return false;
            }
            //5、通过，放行
            return true;
        } catch (Exception ex) {
            //6、不通过，响应401状态码
            BaseContext.removeCurrentId();
            response.setStatus(401);
            return false;
        }
    }

    //请求结束后清理ThreadLocal，避免Tomcat线程复用导致上一个请求的id残留
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }

    //令牌中记录的权限版本，缺失（本次改动前签发的令牌）按0处理
    private int tokenAuthVersion(Claims claims) {
        Object version = claims.get(JwtClaimsConstant.EMP_AUTH_VERSION);
        return version == null ? 0 : Integer.parseInt(version.toString());
    }

    //令牌中的角色是否为店长
    private boolean isManager(Claims claims) {
        Object role = claims.get(JwtClaimsConstant.EMP_ROLE);
        return role != null && RoleConstant.MANAGER.equals(Integer.valueOf(role.toString()));
    }

    //是否为仅店长可访问的接口
    private boolean isManagerOnlyPath(String uri) {
        if (ALL_EMPLOYEE_PATHS.contains(uri)) {
            return false;
        }
        for (String prefix : MANAGER_ONLY_PATH_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    //响应统一Result结构，便于前端直接展示提示
    private void writeNoPermission(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Result.error(MessageConstant.NO_PERMISSION)));
    }
}
