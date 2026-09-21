import { ElMessage } from "element-plus";
import type { ApiError } from "@/api/http";

// 统一的接口错误提示
// - 登录态失效（401）已由 http.ts 统一清态并跳转登录页，这里不再重复弹提示，
//   否则一个页面并发请求全部失败时会刷出一排相同的错误提示
// - 后端返回的业务提示（如"该分类下有关联菜品"）优先展示，网络异常等情况用调用方给的兜底文案
export function showError(e: unknown, fallback: string) {
  const err = e as ApiError | undefined;
  if (err?.authError) {
    return;
  }
  ElMessage.error(err?.fromBackend && err.message ? err.message : fallback);
}
