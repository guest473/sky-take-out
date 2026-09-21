<template>
  <div class="login-page">
    <div class="orb orb-1"></div>
    <div class="orb orb-2"></div>
    <div class="panel page-card">
      <h1 class="title">外卖订餐系统</h1>
      <p class="subtitle">以优雅而克制的设计，管理你的线上餐厅</p>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" autocomplete="off" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="loading"
            @click="onSubmit"
            block
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { adminLogin } from "@/api/admin";
import { useAuthStore } from "@/stores/auth";
import { showError } from "@/utils/error";

const router = useRouter();
const auth = useAuthStore();

const formRef = ref<FormInstance>();
const loading = ref(false);

const form = reactive({
  username: "",
  password: ""
});

const rules: FormRules = {
  username: [{ required: true, message: "请输入账号", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }]
};

const onSubmit = () => {
  if (!formRef.value) return;
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    loading.value = true;
    try {
      const res = await adminLogin(form);
      auth.setAuth({
        token: res.token,
        admin: {
          id: res.id,
          userName: res.userName,
          name: res.name,
          role: res.role
        }
      });
      ElMessage.success("登录成功");
      router.push("/overview");
    } catch (e: any) {
      showError(e, "登录失败");
    } finally {
      loading.value = false;
    }
  });
};
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background: #f3f4f6;
  color: #111827;
}

.panel {
  width: 100%;
  max-width: 420px;
}

.title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
}

.subtitle {
  margin: 0 0 24px;
  font-size: 13px;
  color: #6b7280;
}

.submit-btn {
  width: 100%;
  background: #2563eb;
  border: none;
}
</style>

