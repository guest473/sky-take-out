import { defineStore } from "pinia";

interface AdminInfo {
  id: number | null;
  userName: string | null;
  name: string | null;
  // 角色：1 店长，0 店员
  role: number | null;
}

interface AuthState {
  token: string | null;
  admin: AdminInfo;
}

const TOKEN_KEY = "admin_token";
const ADMIN_KEY = "admin_info";

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    token: localStorage.getItem(TOKEN_KEY),
    admin: {
      id: null,
      userName: null,
      name: null,
      role: null,
      // 兼容旧版本地缓存（没有 role 字段）
      ...(JSON.parse(localStorage.getItem(ADMIN_KEY) || "null") || {})
    }
  }),
  getters: {
    adminName: (state) => state.admin.name,
    // 仅店长可管理员工、查看数据报表、切换营业状态
    isManager: (state) => state.admin.role === 1
  },
  actions: {
    setAuth(payload: { token: string; admin: AdminInfo }) {
      this.token = payload.token;
      this.admin = payload.admin;
      localStorage.setItem(TOKEN_KEY, payload.token);
      localStorage.setItem(ADMIN_KEY, JSON.stringify(payload.admin));
    },
    logout() {
      this.token = null;
      this.admin = { id: null, userName: null, name: null, role: null };
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(ADMIN_KEY);
    }
  }
});

