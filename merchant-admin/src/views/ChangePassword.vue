<template>
  <div class="change-password-page">
    <div class="panel page-card">
      <h1 class="title">修改密码</h1>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" show-password />
        </el-form-item>
        <el-form-item>
          <div class="btn-row">
            <el-button @click="onCancel">取消</el-button>
            <el-button
              type="primary"
              class="submit-btn"
              :loading="loading"
              @click="onSubmit"
            >
              确认修改
            </el-button>
          </div>
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
import { editPassword } from "@/api/admin";
import { useAuthStore } from "@/stores/auth";
import { showError } from "@/utils/error";

const router = useRouter();
const auth = useAuthStore();

const formRef = ref<FormInstance>();
const loading = ref(false);

const form = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: ""
});

const validateConfirm = (
  _rule: any,
  value: string,
  callback: (error?: Error) => void
) => {
  if (value !== form.newPassword) {
    callback(new Error("两次输入的新密码不一致"));
  } else {
    callback();
  }
};

const rules: FormRules = {
  oldPassword: [{ required: true, message: "请输入旧密码", trigger: "blur" }],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" }
  ],
  confirmPassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    { validator: validateConfirm, trigger: "blur" }
  ]
};

const onSubmit = () => {
  if (!formRef.value) return;
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    loading.value = true;
    try {
      await editPassword({
        empId: auth.admin.id!,
        oldPassword: form.oldPassword,
        newPassword: form.newPassword
      });
      ElMessage.success("密码修改成功，请重新登录");
      auth.logout();
      router.push("/login");
    } catch (e: any) {
      showError(e, "修改失败");
    } finally {
      loading.value = false;
    }
  });
};

const onCancel = () => {
  router.back();
};
</script>

<style scoped>
.change-password-page {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
}

.panel {
  width: 100%;
  max-width: 500px;
}

.title {
  margin: 0 0 24px;
  font-size: 20px;
  font-weight: 600;
}

.btn-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}

.submit-btn {
  background: #2563eb;
  border: none;
}
</style>
