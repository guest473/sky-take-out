<template>
  <div class="page-card">
    <div class="header">
      <div>
        <h2 class="title">员工管理</h2>
      </div>
      <el-button type="primary" @click="openCreate">新增员工</el-button>
    </div>

    <el-form :inline="true" :model="query" class="filter">
      <el-form-item label="姓名">
        <el-input v-model="query.name" placeholder="员工姓名" clearable />
      </el-form-item>
      <el-form-item>
        <el-button @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" size="small" :stripe="true" row-key="id" v-loading="loading">
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="username" label="账号" />
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 1 ? 'warning' : 'info'">
            {{ row.role === 1 ? "店长" : "店员" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? "启用" : "禁用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button type="text" @click="openEdit(row)">编辑</el-button>
          <el-button
            type="text"
            :disabled="cannotToggle(row)"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? "禁用" : "启用" }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        @current-change="loadData"
      />
    </div>

    <el-dialog v-model="visible" :title="editing ? '编辑员工' : '新增员工'" width="420px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" maxlength="32" />
        </el-form-item>
        <el-form-item v-if="!editing" label="初始密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="32"
            placeholder="由店长设置，员工登录后可自行修改"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio :value="0">店员</el-radio>
            <el-radio :value="1">店长</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.sex">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idNumber">
          <el-input v-model="form.idNumber" maxlength="18" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRefreshOnFocus } from "@/utils/refreshOnFocus";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { useAuthStore } from "@/stores/auth";
import {
  fetchEmployees,
  createEmployee,
  updateEmployee,
  changeEmployeeStatus
} from "@/api/admin";
import { showError } from "@/utils/error";

const auth = useAuthStore();

// 不能对当前登录账号启用/禁用（后端也会拦截），admin 账号同样受保护
const cannotToggle = (row: any) => row.id === auth.admin.id || row.username === "admin";

const list = ref<any[]>([]);
const total = ref(0);
const loading = ref(false);

const query = reactive({
  page: 1,
  pageSize: 10,
  name: ""
});

const visible = ref(false);
const editing = ref(false);
const formRef = ref<FormInstance>();
const rules: FormRules = {
  name: [{ required: true, message: "请输入员工姓名", trigger: "blur" }],
  username: [{ required: true, message: "请输入登录账号", trigger: "blur" }],
  // 仅新增时渲染，编辑时该字段被 v-if 移除、不参与校验
  password: [{ required: true, message: "请设置初始密码", trigger: "blur" }],
  phone: [
    { required: true, message: "请输入手机号", trigger: "blur" },
    { pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" }
  ],
  idNumber: [{ required: true, message: "请输入身份证号", trigger: "blur" }]
};
const form = reactive<any>({
  id: null,
  name: "",
  username: "",
  password: "",
  sex: "男",
  phone: "",
  idNumber: "",
  role: 0
});

const loadData = async () => {
  loading.value = true;
  try {
    const res: any = await fetchEmployees(query);
    list.value = res.records || res.list || res.data || [];
    total.value = res.total || 0;
  } catch (e) {
    showError(e, "加载员工数据失败");
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  editing.value = false;
  Object.assign(form, {
    id: null,
    name: "",
    username: "",
    password: "",
    sex: "男",
    phone: "",
    idNumber: "",
    role: 0
  });
  visible.value = true;
};

const openEdit = (row: any) => {
  editing.value = true;
  Object.assign(form, {
    id: row.id,
    name: row.name,
    username: row.username,
    // 编辑不改密码（走顶部的「修改密码」），清空避免把旧值随请求发出去
    password: "",
    phone: row.phone,
    sex: row.sex,
    idNumber: row.idNumber,
    role: row.role ?? 0
  });
  visible.value = true;
};

const submit = async () => {
  if (!formRef.value) return;
  // 必填项与库中约束保持一致，避免提交后才由后端报错
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    try {
      if (editing.value) {
        await updateEmployee(form);
        ElMessage.success("更新成功");
      } else {
        await createEmployee(form);
        ElMessage.success("创建成功");
      }
      visible.value = false;
      loadData();
    } catch (e: any) {
      //展示后端返回的具体原因（如"该用户名已存在"）
      showError(e, "保存失败");
    }
  });
};

const toggleStatus = async (row: any) => {
  const targetStatus = row.status === 1 ? 0 : 1;
  try {
    await ElMessageBox.confirm(
      `确定要${targetStatus === 1 ? "启用" : "禁用"}该员工吗？`,
      "提示"
    );
    await changeEmployeeStatus(targetStatus, row.id);
    row.status = targetStatus;
    ElMessage.success("已更新状态");
  } catch (error: any) {
    if (error !== "cancel") {
      showError(error, "更新状态失败");
    }
  }
};

onMounted(() => {
  loadData();
});

useRefreshOnFocus(loadData);
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.title {
  margin: 0 0 4px;
}
.filter {
  margin-bottom: 12px;
}
.pagination {
  margin-top: 12px;
  text-align: right;
}
</style>
