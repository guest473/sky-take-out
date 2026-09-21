package com.sky.mapper;

import com.sky.entity.AddressBook;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AddressBookMapper {

    //条件查询
    List<AddressBook> list(AddressBook addressBook);

    //新增
    @Insert("insert into address_book" +
            "        (user_id, consignee, phone, sex, province_code, province_name, city_code, city_name, district_code," +
            "         district_name, detail, label, is_default)" +
            "        values (#{userId}, #{consignee}, #{phone}, #{sex}, #{provinceCode}, #{provinceName}, #{cityCode}, #{cityName}," +
            "                #{districtCode}, #{districtName}, #{detail}, #{label}, #{isDefault})")
    void insert(AddressBook addressBook);

    //根据id查询
    @Select("select * from address_book where id = #{id}")
    AddressBook getById(Long id);

    //根据id和用户id查询（下单时校验地址归属，避免使用他人收货地址）
    @Select("select * from address_book where id = #{id} and user_id = #{userId}")
    AddressBook getByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    //根据id修改
    void update(AddressBook addressBook);

    //根据用户id修改是否默认地址
    @Update("update address_book set is_default = #{isDefault} where user_id = #{userId}")
    void updateIsDefaultByUserId(AddressBook addressBook);

    //根据id删除地址
    @Delete("delete from address_book where id = #{id}")
    void deleteById(Long id);

}
