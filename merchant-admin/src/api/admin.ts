import http from "./http";

// ========== 后端返回的数据结构（对应 sky-pojo 里的 VO）==========

export interface PageResult<T> {
  total: number;
  records: T[];
}

export interface LoginResult {
  id: number;
  userName: string;
  name: string;
  token: string;
  role: number;
}

export interface Employee {
  id: number;
  username: string;
  name: string;
  phone: string;
  sex: string;
  idNumber: string;
  status: number;
  role: number;
  updateTime?: string;
}

export interface Category {
  id: number;
  type: number;
  name: string;
  sort: number;
  status: number;
  updateTime?: string;
}

export interface DishFlavor {
  id?: number;
  dishId?: number;
  name: string;
  value: string;
}

export interface Dish {
  id: number;
  name: string;
  categoryId: number;
  price: number;
  image: string;
  description: string;
  status: number;
  updateTime?: string;
  flavors?: DishFlavor[];
}

export interface SetmealDish {
  id?: number;
  setmealId?: number;
  dishId: number;
  name: string;
  price: number;
  copies: number;
}

export interface Setmeal {
  id: number;
  categoryId: number;
  name: string;
  price: number;
  status: number;
  description: string;
  image: string;
  updateTime?: string;
  setmealDishes?: SetmealDish[];
}

export interface BusinessData {
  turnover: number;
  validOrderCount: number;
  orderCompletionRate: number;
  unitPrice: number;
  newUsers: number;
}

export interface OrderOverview {
  waitingOrders: number;
  deliveredOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  allOrders: number;
}

export interface GoodsOverview {
  sold: number;
  discontinued: number;
}

// 报表的列表字段后端返回的是逗号分隔字符串（如 "2026-09-01,2026-09-02"），不是数组
export interface TurnoverReport {
  dateList: string;
  turnoverList: string;
}

export interface UserReport {
  dateList: string;
  totalUserList: string;
  newUserList: string;
}

export interface OrderReport {
  dateList: string;
  orderCountList: string;
  validOrderCountList: string;
  totalOrderCount: number;
  validOrderCount: number;
  orderCompletionRate: number;
}

export interface Top10Report {
  nameList: string;
  numberList: string;
}

// ========== 员工 / 认证 ==========
export function adminLogin(payload: { username: string; password: string }): Promise<LoginResult> {
  return http.post("/admin/employee/login", payload);
}

// 退出登录：POST /admin/employee/logout
export function adminLogout() {
  return http.post("/admin/employee/logout");
}

// 员工分页：GET /admin/employee/page
export function fetchEmployees(params: {
  page: number;
  pageSize: number;
  name?: string;
}): Promise<PageResult<Employee>> {
  return http.get("/admin/employee/page", { params });
}

// 修改当前登录员工密码
export function editPassword(data: { empId: number; oldPassword: string; newPassword: string }) {
  return http.put("/admin/employee/editPassword", data);
}

// 注册/新增员工：POST /admin/employee
export function createEmployee(data: any) {
  return http.post("/admin/employee", data);
}

// 编辑员工：PUT /admin/employee
export function updateEmployee(data: any) {
  return http.put("/admin/employee", data);
}

// 启用/禁用员工账号：POST /admin/employee/status/{status}?id={id}
export function changeEmployeeStatus(status: number, id: number) {
  return http.post(`/admin/employee/status/${status}`, null, {
    params: { id }
  });
}

// ========== 分类 ==========

// 分类分页：GET /admin/category/page
export function fetchCategories(params: {
  page: number;
  pageSize: number;
  name?: string;
  type?: number;
}): Promise<PageResult<Category>> {
  return http.get("/admin/category/page", { params });
}

// 新增分类：POST /admin/category
export function createCategory(data: any) {
  return http.post("/admin/category", data);
}

// 修改分类：PUT /admin/category
export function updateCategory(data: any) {
  return http.put("/admin/category", data);
}

// 删除分类：DELETE /admin/category?id=xx
export function deleteCategory(id: number) {
  return http.delete("/admin/category", { params: { id } });
}

// 启用/禁用分类：POST /admin/category/status/{status}?id=xx
export function changeCategoryStatus(status: number, id: number) {
  return http.post(`/admin/category/status/${status}`, null, {
    params: { id }
  });
}

// 根据类型查询分类：GET /admin/category/list?type=1
export function listCategoriesByType(type: number): Promise<Category[]> {
  return http.get("/admin/category/list", { params: { type } });
}

// ========== 菜品 ==========

// 菜品分页：GET /admin/dish/page
export function fetchDishes(params: {
  page: number;
  pageSize: number;
  name?: string;
  categoryId?: number;
  status?: number;
}): Promise<PageResult<Dish>> {
  return http.get("/admin/dish/page", { params });
}

// 新增菜品：POST /admin/dish
export function createDish(data: any) {
  return http.post("/admin/dish", data);
}

// 修改菜品：PUT /admin/dish
export function updateDish(data: any) {
  return http.put("/admin/dish", data);
}

// 菜品起售/停售：POST /admin/dish/status/{status}?id={id}
export function changeDishStatus(status: number, id: number) {
  return http.post(`/admin/dish/status/${status}`, null, {
    params: { id }
  });
}

// 根据分类id查询菜品：GET /admin/dish/list?categoryId=xx
export function listDishesByCategory(categoryId: number): Promise<Dish[]> {
  return http.get("/admin/dish/list", { params: { categoryId } });
}

// 菜品批量删除：DELETE /admin/dish?ids=1&ids=2
export function deleteDishes(ids: number[]) {
  return http.delete("/admin/dish", { params: { ids } });
}

// ========== 套餐 ==========

// 套餐分页：GET /admin/setmeal/page
export function fetchSetmeals(params: {
  page: number;
  pageSize: number;
  name?: string;
  categoryId?: number;
  status?: number;
}): Promise<PageResult<Setmeal>> {
  return http.get("/admin/setmeal/page", { params });
}

// 新增套餐：POST /admin/setmeal
export function createSetmeal(data: any) {
  return http.post("/admin/setmeal", data);
}

// 修改套餐：PUT /admin/setmeal
export function updateSetmeal(data: any) {
  return http.put("/admin/setmeal", data);
}

// 根据 id 查询套餐（含菜品）：GET /admin/setmeal/{id}
export function getSetmealById(id: number): Promise<Setmeal> {
  return http.get(`/admin/setmeal/${id}`);
}

// 删除套餐：DELETE /admin/setmeal?ids=1&ids=2
export function deleteSetmeals(ids: number[]) {
  return http.delete("/admin/setmeal", { params: { ids } });
}

// 套餐起售/停售：POST /admin/setmeal/status/{status}?id=xx
export function changeSetmealStatus(status: number, id: number) {
  return http.post(`/admin/setmeal/status/${status}`, null, {
    params: { id }
  });
}

// ========== 店铺营业状态 ==========

// 查询店铺营业状态：GET /admin/shop/status
export function getShopStatus(): Promise<number> {
  return http.get("/admin/shop/status");
}

// 设置店铺营业状态：PUT /admin/shop/{status}
export function setShopStatus(status: number) {
  return http.put(`/admin/shop/${status}`);
}

// ========== 工作台总览 ==========

export function fetchBusinessData(): Promise<BusinessData> {
  return http.get("/admin/workspace/businessData");
}

export function fetchOrderOverview(): Promise<OrderOverview> {
  return http.get("/admin/workspace/overviewOrders");
}

export function fetchDishOverview(): Promise<GoodsOverview> {
  return http.get("/admin/workspace/overviewDishes");
}

export function fetchSetmealOverview(): Promise<GoodsOverview> {
  return http.get("/admin/workspace/overviewSetmeals");
}

// ========== 订单管理 ==========

// 分页条件查询：GET /admin/order/conditionSearch
export function fetchOrders(params: any) {
  return http.get("/admin/order/conditionSearch", { params });
}

// 订单详情：GET /admin/order/details/{id}
export function getOrderDetail(id: number) {
  return http.get(`/admin/order/details/${id}`);
}

// 接单：PUT /admin/order/confirm
export function confirmOrder(payload: { id: number; status: number }) {
  return http.put("/admin/order/confirm", payload);
}

// 派单：PUT /admin/order/delivery/{id}
export function deliveryOrder(id: number) {
  return http.put(`/admin/order/delivery/${id}`);
}

// 完成订单：PUT /admin/order/complete/{id}
export function completeOrder(id: number) {
  return http.put(`/admin/order/complete/${id}`);
}

// 商家拒单：PUT /admin/order/rejection
export function rejectOrder(payload: { id: number; rejectionReason: string }) {
  return http.put("/admin/order/rejection", payload);
}

// 商家取消订单：PUT /admin/order/cancel
export function cancelOrder(payload: { id: number; cancelReason: string }) {
  return http.put("/admin/order/cancel", payload);
}

// ========== 报表 ==========

export function fetchTurnoverReport(params: { begin: string; end: string }): Promise<TurnoverReport> {
  return http.get("/admin/report/turnoverStatistics", { params });
}

export function fetchUserReport(params: { begin: string; end: string }): Promise<UserReport> {
  return http.get("/admin/report/userStatistics", { params });
}

export function fetchOrderReport(params: { begin: string; end: string }): Promise<OrderReport> {
  return http.get("/admin/report/ordersStatistics", { params });
}

export function fetchSalesTop10(params: { begin: string; end: string }): Promise<Top10Report> {
  return http.get("/admin/report/top10", { params });
}

// ========== 通用上传 ==========

export function uploadFile(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return http.post("/admin/common/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
}

