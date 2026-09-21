import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuthStore } from "@/stores/auth";

const Login = () => import("@/views/Login.vue");
const ChangePassword = () => import("@/views/ChangePassword.vue");
const Overview = () => import("@/views/Overview.vue");
const Employees = () => import("@/views/Employees.vue");
const Categories = () => import("@/views/Categories.vue");
const Dishes = () => import("@/views/Dishes.vue");
const Setmeals = () => import("@/views/Setmeals.vue");
const Orders = () => import("@/views/Orders.vue");
const Reports = () => import("@/views/Reports.vue");

const routes: RouteRecordRaw[] = [
  { path: "/login", name: "login", component: Login },
  { path: "/change-password", name: "changePassword", component: ChangePassword },
  { path: "/", redirect: "/overview" },
  { path: "/overview", name: "overview", component: Overview },
  // managerOnly：仅店长可访问，对应后端 JwtTokenAdminInterceptor 里的店长专属接口
  { path: "/employees", name: "employees", component: Employees, meta: { managerOnly: true } },
  { path: "/categories", name: "categories", component: Categories },
  { path: "/dishes", name: "dishes", component: Dishes },
  { path: "/setmeals", name: "setmeals", component: Setmeals },
  { path: "/orders", name: "orders", component: Orders },
  { path: "/reports", name: "reports", component: Reports, meta: { managerOnly: true } }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  const auth = useAuthStore();
  if (to.path !== "/login" && !auth.token) {
    next("/login");
    return;
  }
  if (to.path === "/login" && auth.token) {
    next("/overview");
    return;
  }
  //店长专属页面：店员直接输入地址访问时拦回概览页（菜单项本就已按角色隐藏）
  //权限的正牌校验在后端（越权请求会被 403 拒绝），这里只是不让页面白进去
  if (to.meta.managerOnly && !auth.isManager) {
    ElMessage.warning("当前账号无权限访问该页面");
    next("/overview");
    return;
  }
  next();
});

export default router;

