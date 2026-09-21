package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.BaseException;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    //新增菜品和对应的口味
    @Transactional
    @CacheEvict(cacheNames = "dishCache", allEntries = true)
    public void saveWithFlavor(DishDTO dishDTO) {
        //名称、分类为库中必填字段，价格是加入购物车/下单的前提，先校验避免写库时报SQL异常
        if (!StringUtils.hasText(dishDTO.getName())) {
            throw new BaseException(MessageConstant.DISH_NAME_IS_NULL);
        }
        if (dishDTO.getCategoryId() == null) {
            throw new BaseException(MessageConstant.DISH_CATEGORY_IS_NULL);
        }
        if (dishDTO.getPrice() == null) {
            throw new BaseException(MessageConstant.DISH_PRICE_IS_NULL);
        }

        Dish dish = new Dish();

        BeanUtils.copyProperties(dishDTO, dish);
        //未指定状态时按库默认值起售
        if (dish.getStatus() == null) {
            dish.setStatus(StatusConstant.ENABLE);
        }
        dish.setCreateTime(LocalDateTime.now());
        dish.setUpdateTime(LocalDateTime.now());
        dish.setCreateUser(BaseContext.getCurrentId());
        dish.setUpdateUser(BaseContext.getCurrentId());

        //向菜品表插入1条数据
        dishMapper.insert(dish);

        //获取insert语句生成的主键值
        Long dishId = dish.getId();

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            //向口味表插入n条数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    //菜品分页查询
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        //分页参数兜底：未传或非法时按第1页、每页10条
        int pageNum = dishPageQueryDTO.getPage() < 1 ? 1 : dishPageQueryDTO.getPage();
        int pageSize = dishPageQueryDTO.getPageSize() < 1 ? 10 : dishPageQueryDTO.getPageSize();
        PageHelper.startPage(pageNum, pageSize);
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    //菜品批量删除
    @Transactional
    @CacheEvict(cacheNames = "dishCache", allEntries = true)
    public void deleteBatch(List<Long> ids) {
        //判断当前菜品是否能够删除---是否存在起售中的菜品？？
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (dish == null) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_NOT_FOUND);
            }
            if (StatusConstant.ENABLE.equals(dish.getStatus())) {
                //当前菜品处于起售中，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        //判断当前菜品是否能够删除---是否被套餐关联了？？
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && setmealIds.size() > 0) {
            //当前菜品被套餐关联了，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        //删除菜品表中的菜品数据
        for (Long id : ids) {
            dishMapper.deleteById(id);
            //删除菜品关联的口味数据
            dishFlavorMapper.deleteByDishId(id);
        }
    }

    //根据id查询菜品和对应的口味数据
    public DishVO getByIdWithFlavor(Long id) {
        //根据id查询菜品数据
        Dish dish = dishMapper.getById(id);

        //根据菜品id查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        //将查询到的数据封装到VO
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    //根据id修改菜品基本信息和对应的口味信息
    @Transactional
    @CacheEvict(cacheNames = "dishCache", allEntries = true)
    public void updateWithFlavor(DishDTO dishDTO) {
        //名称是必填项：传了但为空白同样拒绝（未传null表示不修改该列）
        if (dishDTO.getName() != null && !StringUtils.hasText(dishDTO.getName())) {
            throw new BaseException(MessageConstant.DISH_NAME_IS_NULL);
        }

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        dish.setUpdateTime(LocalDateTime.now());
        dish.setUpdateUser(BaseContext.getCurrentId());

        //修改菜品表基本信息
        dishMapper.update(dish);

        //删除原有的口味数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        //重新插入口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId());
            });
            //向口味表插入n条数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    //菜品起售停售
    //停售会同步停售关联套餐，所以两个缓存一起清理
    @Transactional
    @CacheEvict(cacheNames = {"dishCache", "setmealCache"}, allEntries = true)
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);

        if (StatusConstant.DISABLE.equals(status)) {
            // 如果是停售操作，还需要将包含当前菜品的套餐也停售
            List<Long> dishIds = new ArrayList<>();
            dishIds.add(id);
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);
            if (setmealIds != null && setmealIds.size() > 0) {
                for (Long setmealId : setmealIds) {
                    Setmeal setmeal = Setmeal.builder()
                            .id(setmealId)
                            .status(StatusConstant.DISABLE)
                            .build();
                    setmealMapper.update(setmeal);
                }
            }
        }
    }

    //根据分类id查询菜品
    public List<Dish> list(Long categoryId) {
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return dishMapper.list(dish);
    }

    //条件查询菜品和口味（用户端菜单）：结果按分类缓存，菜品增删改后由上面的写方法统一清理
    //key 用分类id，未传分类时兜底为 all：SpEL 结果为 null 时 Spring 会抛 IllegalArgumentException
    @Cacheable(cacheNames = "dishCache", key = "#dish.categoryId == null ? 'all' : #dish.categoryId")
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        //一次性取回所有菜品的口味并按菜品id分组，避免逐个菜品查询
        Map<Long, List<DishFlavor>> flavorMap = new HashMap<>();
        if (!dishList.isEmpty()) {
            flavorMap = dishFlavorMapper
                    .getByDishIds(dishList.stream().map(Dish::getId).collect(Collectors.toList()))
                    .stream()
                    .collect(Collectors.groupingBy(DishFlavor::getDishId));
        }

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            dishVO.setFlavors(flavorMap.getOrDefault(d.getId(), new ArrayList<>()));
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }
}
