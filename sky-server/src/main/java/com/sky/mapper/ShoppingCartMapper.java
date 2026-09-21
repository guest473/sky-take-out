package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    List<ShoppingCart> getById(ShoppingCart shoppingCart);

    void insert(ShoppingCart shoppingCart);

    //原子累加份数：同一用户同一商品不存在则插入，存在则份数+1
    void insertOrIncrement(ShoppingCart shoppingCart);

    //原子减一：仅当份数大于1时才生效，返回受影响行数
    @Update("update shopping_cart set number = number - 1 where id = #{id} and number > 1")
    int decrementIfMoreThanOne(Long id);

    //份数只剩1时删除该行，返回受影响行数
    @Delete("delete from shopping_cart where id = #{id} and number = 1")
    int deleteIfNumberIsOne(Long id);

    @Delete("delete from shopping_cart where user_id = #{userId}")
    void clean(Long userId);

    //查询购物车中已不可售的商品名：菜品/套餐已停售、所属分类已停用，或商品已被删除
    List<String> listNotSellableNames(@Param("userId") Long userId);
}
