package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.sky.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    // 根据openid查询用户(微信登录)
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    void insert(User user);

    Integer countByMap(Map map);

    //按日聚合新增用户数：报表一次取回整个区间，避免逐日查询
    List<Map<String, Object>> countNewUserByDay(@Param("begin") LocalDateTime begin,
                                               @Param("end") LocalDateTime end);
}
