<template>
  <router-view v-if="isLogin" />
  <div v-else class="layout">
    <el-container class="layout-container">
      <el-aside width="220px" class="aside">
        <div class="logo">外卖商家端</div>
        <div class="menu-wrap">
          <el-menu
            router
            :default-active="route.path"
            background-color="#f9fafb"
            text-color="#4b5563"
            active-text-color="#2563eb"
          >
            <el-menu-item index="/overview">概览</el-menu-item>
            <el-sub-menu index="goods">
              <template #title>菜品与套餐</template>
              <el-menu-item index="/categories">分类管理</el-menu-item>
              <el-menu-item index="/dishes">菜品管理</el-menu-item>
              <el-menu-item index="/setmeals">套餐管理</el-menu-item>
            </el-sub-menu>
            <el-menu-item v-if="auth.isManager" index="/employees">员工管理</el-menu-item>
            <el-menu-item index="/orders">订单管理</el-menu-item>
            <el-menu-item v-if="auth.isManager" index="/reports">数据报表</el-menu-item>
          </el-menu>
        </div>
        <div class="user-bar">
          <div class="avatar">{{ avatarText }}</div>
          <span class="account" :title="accountName">{{ accountName }}</span>
          <el-dropdown trigger="click" placement="top-end" @command="onUserCommand">
            <span class="gear" title="账号设置">
              <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"
                />
              </svg>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-aside>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onBeforeUnmount } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "./stores/auth";
import { wsService } from "./utils/websocket";
import { adminLogout } from "./api/admin";

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const isLogin = computed(() => route.path === "/login");
// 侧边栏底部展示当前登录账号（账号名 + 首字母头像）
const accountName = computed(() => auth.admin.userName || auth.admin.name || "管理员");
const avatarText = computed(() => (accountName.value.trim().charAt(0) || "?").toUpperCase());

watch(
  () => auth.admin.id,
  (newId) => {
    if (newId !== null && newId !== undefined) {
      wsService.connect(String(newId));
    } else {
      wsService.disconnect();
    }
  },
  { immediate: true }
);

const onLogout = async () => {
  try {
    //先通知后端退出，接口异常不影响本地登录态清理
    await adminLogout();
  } catch (e) {
    console.warn("[Logout] 后端退出接口调用失败", e);
  }
  auth.logout();
  wsService.disconnect();
  router.push("/login");
};

const onChangePassword = () => {
  router.push("/change-password");
};

const onUserCommand = (command: string) => {
  if (command === "logout") {
    onLogout();
  } else if (command === "password") {
    onChangePassword();
  }
};

onBeforeUnmount(() => {
  wsService.disconnect();
});
</script>

<style scoped>
.layout {
  height: 100vh;
  display: flex;
  background: #e5e7eb;
  color: #111827;
}
.layout-container {
  min-width: 0;
}
.aside {
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e5e7eb;
  background: #f9fafb;
}
.logo {
  flex: none;
  padding: 20px 16px;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
  letter-spacing: 0.08em;
}
.menu-wrap {
  flex: 1;
  overflow-y: auto;
}
.user-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-top: 1px solid #e5e7eb;
}
.avatar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #2563eb;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  text-transform: uppercase;
}
.account {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #4b5563;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.gear {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  cursor: pointer;
  outline: none;
}
.gear:hover {
  color: #2563eb;
}
.main {
  display: flex;
  flex-direction: column;
  background: #f3f4f6;
}
.main :deep(.page-card) {
  flex: 1 0 auto;
}
</style>

