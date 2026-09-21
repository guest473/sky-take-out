package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper {

    //根据用户名查询员工
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    //插入员工数据
    @Insert("insert into employee (name, username, password, phone, sex, id_number, create_time, update_time, create_user, update_user, status, role) " +
            "values " +
            "(#{name},#{username},#{password},#{phone},#{sex},#{idNumber},#{createTime},#{updateTime},#{createUser},#{updateUser},#{status},#{role})")
    void insert(Employee employee);

    //分页查询
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    //根据主键动态修改属性
    void update(Employee employee);

    //根据id查询员工信息
    @Select("select * from employee where id = #{id}")
    Employee getById(Long id);

    //统计除指定员工之外，仍处于启用状态的店长数量（role=1 店长，status=1 启用）
    @Select("select count(id) from employee where role = 1 and status = 1 and id <> #{id}")
    int countOtherEnabledManagers(Long id);
}
