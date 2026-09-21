package com.sky.dto;

import com.sky.constant.MessageConstant;
import com.sky.entity.SetmealDish;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class SetmealDTO implements Serializable {

    private Long id;

    //分类id
    @NotNull(message = MessageConstant.SETMEAL_CATEGORY_IS_NULL)
    private Long categoryId;

    //套餐名称
    @NotBlank(message = MessageConstant.SETMEAL_NAME_IS_NULL)
    private String name;

    //套餐价格
    @NotNull(message = MessageConstant.SETMEAL_PRICE_IS_NULL)
    private BigDecimal price;

    //状态 0:停用 1:启用
    private Integer status;

    //描述信息
    private String description;

    //图片
    private String image;

    //套餐菜品关系
    @NotEmpty(message = MessageConstant.SETMEAL_DISH_IS_NULL)
    private List<SetmealDish> setmealDishes = new ArrayList<>();

}
