package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    @Transactional
    public ShoppingCart addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        //名称、图片、单价一律取服务端数据，不采用客户端传入的值
        if (shoppingCartDTO.getDishId() != null) {
            Dish dish = dishMapper.getById(shoppingCartDTO.getDishId());
            if (dish == null) {
                throw new ShoppingCartBusinessException(MessageConstant.DISH_NOT_FOUND);
            }
            //停售的菜品、所属分类被禁用的菜品都不可加购
            checkSellable(dish.getStatus(), dish.getCategoryId(), MessageConstant.DISH_NOT_ON_SALE);
            shoppingCart.setAmount(dish.getPrice());
            shoppingCart.setImage(dish.getImage());
            shoppingCart.setName(dish.getName());
        } else {
            Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
            if (setmeal == null) {
                throw new ShoppingCartBusinessException(MessageConstant.SETMEAL_NOT_FOUND);
            }
            //停售的套餐、所属分类被禁用的套餐都不可加购
            checkSellable(setmeal.getStatus(), setmeal.getCategoryId(), MessageConstant.SETMEAL_NOT_ON_SALE);
            shoppingCart.setAmount(setmeal.getPrice());
            shoppingCart.setImage(setmeal.getImage());
            shoppingCart.setName(setmeal.getName());
        }
        shoppingCart.setNumber(1);
        shoppingCart.setCreateTime(LocalDateTime.now());
        //原子累加：不存在则插入，已存在则份数+1；由唯一索引保证并发点击不会插入重复行
        shoppingCartMapper.insertOrIncrement(shoppingCart);
        return getCartItem(userId, shoppingCartDTO);
    }

    //查询当前用户购物车中的指定商品（含口味）
    private ShoppingCart getCartItem(Long userId, ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart query = new ShoppingCart();
        query.setUserId(userId);
        query.setDishId(shoppingCartDTO.getDishId());
        query.setSetmealId(shoppingCartDTO.getSetmealId());
        query.setDishFlavor(shoppingCartDTO.getDishFlavor());
        List<ShoppingCart> list = shoppingCartMapper.getById(query);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    //商品可售的前提：自身处于起售状态，且所属分类处于启用状态（分类被禁用时整类商品不可售）
    private void checkSellable(Integer status, Long categoryId, String notOnSaleMessage) {
        if (!StatusConstant.ENABLE.equals(status)) {
            throw new ShoppingCartBusinessException(notOnSaleMessage);
        }
        if (categoryId == null || categoryMapper.countEnabledById(categoryId) == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.CATEGORY_DISABLED);
        }
    }

    @Override
    public List<ShoppingCart> list(ShoppingCart shoppingCart) {
        //只查当前登录用户的购物车，忽略客户端传入的userId
        shoppingCart.setUserId(BaseContext.getCurrentId());
        return shoppingCartMapper.getById(shoppingCart);
    }

    @Override
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart item = getCartItem(BaseContext.getCurrentId(), shoppingCartDTO);
        if (item == null) {
            return;
        }
        //原子操作：份数为1时删除，否则减一，并发下不会出现0或负数
        if (shoppingCartMapper.deleteIfNumberIsOne(item.getId()) == 0) {
            shoppingCartMapper.decrementIfMoreThanOne(item.getId());
        }
    }

    @Override
    public void clean() {
        //只清理当前登录用户的购物车
        shoppingCartMapper.clean(BaseContext.getCurrentId());
    }
}
