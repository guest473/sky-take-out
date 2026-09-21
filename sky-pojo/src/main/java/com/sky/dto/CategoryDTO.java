package com.sky.dto;

import com.sky.constant.MessageConstant;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class CategoryDTO implements Serializable {

    //主键
    private Long id;

    //类型 1 菜品分类 2 套餐分类
    private Integer type;

    //分类名称
    @NotBlank(message = MessageConstant.CATEGORY_NAME_IS_NULL)
    private String name;

    //排序
    private Integer sort;

}
