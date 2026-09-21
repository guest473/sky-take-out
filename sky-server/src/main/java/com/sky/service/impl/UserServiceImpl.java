package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.exception.LoginFailedException;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import com.sky.mapper.UserMapper;
import com.sky.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    private static final String WX_LOGIN ="https://api.weixin.qq.com/sns/jscode2session";
    @Autowired
    private  UserMapper userMapper;
    @Autowired
    private  WeChatProperties weChatProperties;

    public UserServiceImpl(WeChatProperties weChatProperties) {
        this.weChatProperties = weChatProperties;
    }

    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        //不打印入参：DTO 里是微信登录凭证 code
        log.info("微信用户登录");
        Map<String,String> map = new HashMap<>();
        map.put("appid",weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("js_code",userLoginDTO.getCode());
        map.put("grant_type","authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, map);
        JSONObject jsonObject = JSON.parseObject(json);
        //微信接口未返回可解析内容时（网络失败/返回非200）统一按登录失败处理，避免在下面抛NPE
        if (jsonObject == null) {
            log.warn("微信登录接口未返回有效响应：{}", json);
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        String openid = jsonObject.getString("openid");

        if(openid == null)
           throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        User user =userMapper.getByOpenid(openid);
        if(user== null){
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }
        return user;
    }
}
