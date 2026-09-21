package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.BaseException;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

//套餐业务实现

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    //新增套餐，同时需要保存套餐和菜品的关联关系

    @Transactional
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public void saveWithDish(SetmealDTO setmealDTO) {
        //名称、分类、价格为库中必填字段，先校验避免写库时才报SQL异常
        if (!StringUtils.hasText(setmealDTO.getName())) {
            throw new BaseException(MessageConstant.SETMEAL_NAME_IS_NULL);
        }
        if (setmealDTO.getCategoryId() == null) {
            throw new BaseException(MessageConstant.SETMEAL_CATEGORY_IS_NULL);
        }
        if (setmealDTO.getPrice() == null) {
            throw new BaseException(MessageConstant.SETMEAL_PRICE_IS_NULL);
        }

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes == null || setmealDishes.isEmpty()) {
            throw new BaseException(MessageConstant.SETMEAL_DISH_IS_NULL);
        }

        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        //未指定状态时按库默认值起售
        if (setmeal.getStatus() == null) {
            setmeal.setStatus(StatusConstant.ENABLE);
        }

        setmeal.setCreateTime(LocalDateTime.now());
        setmeal.setUpdateTime(LocalDateTime.now());
        setmeal.setCreateUser(BaseContext.getCurrentId());
        setmeal.setUpdateUser(BaseContext.getCurrentId());

        //向套餐表插入数据
        setmealMapper.insert(setmeal);

        //获取生成的套餐id
        Long setmealId = setmeal.getId();

        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });

        //保存套餐和菜品的关联关系
        setmealDishMapper.insertBatch(setmealDishes);
    }

    //分页查询
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        //分页参数兜底：未传或非法时按第1页、每页10条
        int pageNum = setmealPageQueryDTO.getPage() < 1 ? 1 : setmealPageQueryDTO.getPage();
        int pageSize = setmealPageQueryDTO.getPageSize() < 1 ? 10 : setmealPageQueryDTO.getPageSize();

        PageHelper.startPage(pageNum, pageSize);
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    //批量删除套餐
    @Transactional
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public void deleteBatch(List<Long> ids) {
        ids.forEach(id -> {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal == null) {
                throw new BaseException(MessageConstant.SETMEAL_NOT_FOUND);
            }
            if (Objects.equals(StatusConstant.ENABLE, setmeal.getStatus())) {
                //起售中的套餐不能删除
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        });

        ids.forEach(setmealId -> {
            //删除套餐表中的数据
            setmealMapper.deleteById(setmealId);
            //删除套餐菜品关系表中的数据
            setmealDishMapper.deleteBySetmealId(setmealId);
        });
    }

    //根据id查询套餐和套餐菜品关系
    public SetmealVO getByIdWithDish(Long id) {
        SetmealVO setmealVO = setmealMapper.getByIdWithDish(id);
        return setmealVO;
    }

    //修改套餐
    @Transactional
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public void update(SetmealDTO setmealDTO) {
        //名称是必填项：传了但为空白同样拒绝（未传null表示不修改该列）
        if (setmealDTO.getName() != null && !StringUtils.hasText(setmealDTO.getName())) {
            throw new BaseException(MessageConstant.SETMEAL_NAME_IS_NULL);
        }

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes == null || setmealDishes.isEmpty()) {
            throw new BaseException(MessageConstant.SETMEAL_DISH_IS_NULL);
        }

        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        setmeal.setUpdateTime(LocalDateTime.now());
        setmeal.setUpdateUser(BaseContext.getCurrentId());

        //1、修改套餐表，执行update
        setmealMapper.update(setmeal);

        //套餐id
        Long setmealId = setmealDTO.getId();

        //2、删除套餐和菜品的关联关系，操作setmeal_dish表，执行delete
        setmealDishMapper.deleteBySetmealId(setmealId);

        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });
        //3、重新插入套餐和菜品的关联关系，操作setmeal_dish表，执行insert
        setmealDishMapper.insertBatch(setmealDishes);
    }

    //套餐起售、停售
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public void startOrStop(Integer status, Long id) {
        //起售套餐时，判断套餐内是否有停售菜品，有停售菜品提示"套餐内包含未启售菜品，无法启售"
        if (Objects.equals(StatusConstant.ENABLE, status)) {
            //select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = ?
            List<Dish> dishList = dishMapper.getBySetmealId(id);
            if (dishList != null && dishList.size() > 0) {
                dishList.forEach(dish -> {
                    if (Objects.equals(StatusConstant.DISABLE, dish.getStatus())) {
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }

    //条件查询（用户端套餐列表）：结果按分类缓存，套餐增删改后由上面的写方法统一清理
    //key 用分类id，未传分类时兜底为 all：SpEL 结果为 null 时 Spring 会抛 IllegalArgumentException
    @Cacheable(cacheNames = "setmealCache", key = "#setmeal.categoryId == null ? 'all' : #setmeal.categoryId")
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    //根据id查询菜品选项
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
