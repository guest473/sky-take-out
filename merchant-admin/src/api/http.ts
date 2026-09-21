import axios, { type AxiosRequestConfig } from "axios";
import { useAuthStore } from "@/stores/auth";
import router from "@/router";

const http = axios.create({
  baseURL: "",
  timeout: 15000,
  // 数组参数按 ids=1&ids=2 序列化，匹配后端 @RequestParam List<Long> ids
  paramsSerializer: { indexes: null }
});

http.interceptors.request.use((config) => {
  const auth = useAuthStore();
  if (auth.token) {
    // 后端 application.yml 中配置的 admin-token-name 为 token
    config.headers = config.headers || {};
    (config.headers as any).token = auth.token;
  }
  return config;
});

http.interceptors.response.use(
  (resp) => {
    const data = resp.data;
    // 后端统一 Result<T> 包装，形如 { code, msg, data }
    if (data && typeof data.code !== "undefined") {
      if (data.code !== 1) {
        // fromBackend：message 是后端给的业务提示，调用方可以直接展示
        const err = new Error(data.msg || "请求失败") as ApiError;
        err.fromBackend = true;
        return Promise.reject(err);
      }
      // 只解包 data。不能写成 data.data ?? data：
      // 后端返回 Result.success()（无 data，序列化后为 null）时，
      // 兜底会把整个 Result 对象当成业务数据返回，调用方拿到的就不是接口返回的内容了
      return data.data;
    }
    // 非 Result 结构的响应原样返回
    return data;
  },
  (error) => {
    // 令牌失效：清理登录态并回到登录页，避免页面卡在请求全部失败的状态
    if (error.response && error.response.status === 401) {
      const auth = useAuthStore();
      auth.logout();
      if (router.currentRoute.value.path !== "/login") {
        router.push("/login");
      }
      // authError：并发请求会同时失败，由调用方统一跳过提示，避免刷屏
      const err = new Error("登录已过期，请重新登录") as ApiError;
      err.fromBackend = true;
      err.authError = true;
      return Promise.reject(err);
    }
    // 无权限：把后端的提示原样透出（如角色不足）
    if (error.response && error.response.status === 403) {
      const msg = error.response.data && error.response.data.msg;
      const err = new Error(msg || "无权限访问") as ApiError;
      err.fromBackend = true;
      return Promise.reject(err);
    }
    return Promise.reject(error);
  }
);

//响应拦截器已把后端 Result<T> 解包成 T，这里让类型与运行时行为一致
//（axios 默认声明为 AxiosResponse，与实际不符，是此前所有 res.xxx 取不到字段的根因）
//泛型默认 any：未显式指定类型时保持宽松，可按接口逐个补上具体类型
export interface HttpClient {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  post<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  put<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
}

// 拦截器构造的错误带的标记，供 utils/error.ts 判断该不该弹提示
export interface ApiError extends Error {
  // message 来自后端，可直接展示
  fromBackend?: boolean;
  // 登录态失效，已统一跳转登录页
  authError?: boolean;
}

export default http as unknown as HttpClient;

