package com.sky.websocket;

import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.StatusConstant;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

// WebSocket服务
@Component
@ServerEndpoint(value = "/ws/{sid}", configurator = WebSocketServer.AuthHandshakeConfigurator.class)
@Slf4j
public class WebSocketServer {

    //存放会话对象：key 用会话id而不是客户端传的 sid
    //sid 是客户端给的（前端用的是登录员工id），同一员工开两个页面时 sid 相同，
    //用 sid 作 key 会让后一个连接静默覆盖前一个——旧连接既不关闭、也再收不到推送
    private static Map<String, Session> sessionMap = new ConcurrentHashMap();

    //WebSocket端点由容器按连接实例化，密钥放在静态字段，保证任何实例都能读到
    private static String adminSecretKey;

    //同上，握手时校验账号状态与权限版本需要用到
    private static EmployeeService employeeService;

    @Autowired
    public void setJwtProperties(JwtProperties jwtProperties) {
        WebSocketServer.adminSecretKey = jwtProperties.getAdminSecretKey();
    }

    @Autowired
    public void setEmployeeService(EmployeeService employeeService) {
        WebSocketServer.employeeService = employeeService;
    }

    //把客户端请求的子协议原样回显：浏览器要求服务端确认子协议，否则会判定握手失败
    //令牌通过子协议传递，不再出现在 URL 查询串里（查询串会被代理与网关的访问日志记录下来）
    public static class AuthHandshakeConfigurator extends ServerEndpointConfig.Configurator {
        @Override
        public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
            return requested.isEmpty() ? "" : requested.get(0);
        }
    }

    //连接建立成功调用的方法
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        //握手时校验管理端令牌，未授权直接断开，避免任意客户端订阅订单提醒
        if (!isAuthorized(session)) {
            log.warn("客户端{}未携带有效令牌，拒绝建立WebSocket连接", sid);
            closeQuietly(session);
            return;
        }
        log.info("客户端{}建立连接，会话id={}", sid, session.getId());
        sessionMap.put(session.getId(), session);
    }

    //校验子协议中携带的管理端令牌：除了签名有效，还要确认账号仍启用且令牌未被权限变更作废
    private boolean isAuthorized(Session session) {
        if (adminSecretKey == null || employeeService == null) {
            return false;
        }
        String token = session.getNegotiatedSubprotocol();
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            Claims claims = JwtUtil.parseJWT(adminSecretKey, token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            //账号不存在（已删除）或被禁用时，不允许继续订阅订单提醒
            Employee employee = employeeService.getById(empId);
            if (Objects.equals(StatusConstant.DISABLE, employee.getStatus())) {
                return false;
            }
            //与HTTP接口一致：角色/密码变更后签发的旧令牌立即失效，这里同样按权限版本比对
            Object version = claims.get(JwtClaimsConstant.EMP_AUTH_VERSION);
            int tokenVersion = version == null ? 0 : Integer.parseInt(version.toString());
            return tokenVersion == employeeService.getAuthVersion(empId);
        } catch (Exception e) {
            return false;
        }
    }

    private void closeQuietly(Session session) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized"));
        } catch (IOException e) {
            log.warn("关闭WebSocket会话失败", e);
        }
    }

    //收到客户端消息后调用的方法
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.debug("收到来自客户端{}的信息：{}", sid, message);
    }

    //连接关闭调用的方法
    @OnClose
    public void onClose(@PathParam("sid") String sid, Session session) {
        log.info("连接断开：{}（会话id={}）", sid, session.getId());
        sessionMap.remove(session.getId());
    }

    //群发
    public void sendToAllClient(String message) {
        Collection<Session> sessions = sessionMap.values();
        for (Session session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                //异步端点 + 每会话串行化：同一连接不能被多线程同时写入，否则会抛异常并丢消息
                synchronized (session) {
                    session.getAsyncRemote().sendText(message);
                }
            } catch (Exception e) {
                log.warn("向客户端{}推送消息失败", session.getId(), e);
            }
        }
    }

}
