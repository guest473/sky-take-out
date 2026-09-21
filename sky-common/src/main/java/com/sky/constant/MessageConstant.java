package com.sky.constant;

//信息提示常量类
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    //登录时统一提示：不区分"账号不存在"与"密码错误"，避免用户名枚举
    public static final String ACCOUNT_OR_PASSWORD_ERROR = "用户名或密码错误";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String NO_PERMISSION = "当前账号无权限访问该功能";
    public static final String ALREADY_EXISTS = "已存在";
    public static final String ACCOUNT_ALREADY_EXISTS = "该用户名已存在";
    public static final String LOGIN_FAIL_EXCEEDED = "登录失败次数过多，账号已临时锁定，请稍后再试";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String CATEGORY_BE_RELATED_BY_SETMEAL = "当前分类关联了套餐,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DISH = "当前分类关联了菜品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    public static final String ADDRESS_BOOK_IS_NULL = "用户地址为空，不能下单";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String UPLOAD_TYPE_NOT_ALLOWED = "只允许上传 jpg/jpeg/png/gif/bmp/webp 格式的图片";
    public static final String SETMEAL_ENABLE_FAILED = "套餐内包含未启售菜品，无法启售";
    public static final String SETMEAL_DISH_IS_NULL = "套餐至少需要包含一个菜品";
    public static final String DISH_ON_SALE = "起售中的菜品不能删除";
    public static final String DISH_NOT_FOUND = "菜品不存在";
    public static final String SETMEAL_ON_SALE = "起售中的套餐不能删除";
    public static final String SETMEAL_NOT_FOUND = "套餐不存在";
    public static final String DISH_BE_RELATED_BY_SETMEAL = "当前菜品关联了套餐,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String ORDER_REPEAT_SUBMIT = "请勿重复提交订单";

    //必填字段校验提示
    public static final String PHONE_IS_NULL = "手机号不能为空";
    public static final String PASSWORD_IS_NULL = "密码不能为空";
    public static final String NEW_PASSWORD_IS_NULL = "新密码不能为空";
    public static final String WECHAT_CODE_IS_NULL = "微信登录凭证不能为空";
    public static final String ORDER_NUMBER_IS_NULL = "订单号不能为空";
    public static final String CATEGORY_NAME_IS_NULL = "分类名称不能为空";
    public static final String DISH_NAME_IS_NULL = "菜品名称不能为空";
    public static final String DISH_CATEGORY_IS_NULL = "菜品分类不能为空";
    public static final String DISH_PRICE_IS_NULL = "菜品价格不能为空";
    public static final String SETMEAL_NAME_IS_NULL = "套餐名称不能为空";
    public static final String SETMEAL_CATEGORY_IS_NULL = "套餐分类不能为空";
    public static final String SETMEAL_PRICE_IS_NULL = "套餐价格不能为空";
    public static final String EMPLOYEE_NAME_IS_NULL = "员工姓名不能为空";
    public static final String EMPLOYEE_USERNAME_IS_NULL = "登录账号不能为空";
    public static final String EMPLOYEE_SEX_IS_NULL = "性别不能为空";
    public static final String EMPLOYEE_ID_NUMBER_IS_NULL = "身份证号不能为空";
    public static final String EMPLOYEE_ID_IS_NULL = "员工id不能为空";
    public static final String MANAGER_MUST_KEEP_ONE = "系统至少需要保留一名启用中的店长，请先指定其他店长再修改";
    public static final String ADDRESS_BOOK_NOT_FOUND = "地址不存在";
    public static final String REPORT_DATE_IS_NULL = "请选择开始日期和结束日期";
    public static final String REPORT_DATE_ILLEGAL = "开始日期不能晚于结束日期";

    //商品可售性校验提示（分类被禁用时整类商品不可售）
    public static final String DISH_NOT_ON_SALE = "菜品已停售，无法购买";
    public static final String SETMEAL_NOT_ON_SALE = "套餐已停售，无法购买";
    public static final String CATEGORY_DISABLED = "商品所属分类已停用，无法购买";
    public static final String GOODS_NOT_SELLABLE = "「%s」已下架，请从购物车移除后重新下单";

}
