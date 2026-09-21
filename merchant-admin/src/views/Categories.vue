<template>
  <div class="page-card">
    <div class="header">
      <div>
        <h2 class="title">分类管理</h2>
      </div>
      <el-button type="primary" @click="openCreate">新增分类</el-button>
    </div>

    <el-segmented v-model="query.type" :options="typeOptions" class="segment" />

    <el-form :inline="true" :model="query" class="filter">
      <el-form-item label="名称">
        <el-input v-model="query.name" placeholder="分类名称" clearable />
      </el-form-item>
      <el-form-item>
        <el-button @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" size="small" stripe row-key="id" v-loading="loading">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? "启用" : "禁用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button type="text" @click="openEdit(row)">编辑</el-button>
          <el-button type="text" @click="toggleStatus(row)">
            {{ row.status === 1 ? "禁用" : "启用" }}
          </el-button>
          <el-popconfirm title="确定删除该分类吗？" @confirm="remove(row)">
            <template #reference>
              <el-button type="text">删除</el-button>
            </template>
          </el-popconfirm>
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

    <el-dialog v-model="visible" :title="editing ? '编辑分类' : '新增分类'" width="420px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
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
import { onMounted, reactive, ref, watch } from "vue";
import { useRefreshOnFocus } from "@/utils/refreshOnFocus";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import {
  fetchCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  changeCategoryStatus
} from "@/api/admin";
import { showError } from "@/utils/error";

const list = ref<any[]>([]);
const total = ref(0);
const loading = ref(false);

const query = reactive({
  page: 1,
  pageSize: 10,
  name: "",
  type: 1
});

const typeOptions = [
  { label: "菜品分类", value: 1 },
  { label: "套餐分类", value: 2 }
];

const visible = ref(false);
const editing = ref(false);
const formRef = ref<FormInstance>();
const rules: FormRules = {
  name: [{ required: true, message: "请输入分类名称", trigger: "blur" }]
};
const form = reactive<any>({
  id: null,
  name: "",
  sort: 0
});

const loadData = async () => {
  loading.value = true;
  try {
    const res: any = await fetchCategories(query);
    list.value = res.records || [];
    total.value = res.total || 0;
  } catch (e) {
    showError(e, "加载分类失败");
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  editing.value = false;
  Object.assign(form, { id: null, name: "", sort: 0 });
  visible.value = true;
};

const openEdit = (row: any) => {
  editing.value = true;
  Object.assign(form, { id: row.id, name: row.name, sort: row.sort });
  visible.value = true;
};

const submit = async () => {
  if (!formRef.value) return;
  // 必填项与库中约束保持一致，避免提交后才由后端报错
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    try {
      const payload = { ...form, type: query.type };
      if (editing.value) {
        await updateCategory(payload);
        ElMessage.success("更新成功");
      } else {
        await createCategory(payload);
        ElMessage.success("创建成功");
      }
      visible.value = false;
      loadData();
    } catch (e: any) {
      showError(e, "保存失败");
    }
  });
};

const toggleStatus = async (row: any) => {
  const target = row.status === 1 ? 0 : 1;
  try {
    await ElMessageBox.confirm(
      `确定要${target === 1 ? "启用" : "禁用"}该分类吗？`,
      "提示"
    );
    await changeCategoryStatus(target, row.id);
    row.status = target;
    ElMessage.success("已更新状态");
  } catch (error: any) {
    if (error !== "cancel") {
      showError(error, "更新状态失败");
    }
  }
};

const remove = async (row: any) => {
  try {
    await deleteCategory(row.id);
    ElMessage.success("已删除");
    loadData();
  } catch (e: any) {
    showError(e, "删除失败");
  }
};

watch(
  () => query.type,
  () => {
    query.page = 1;
    loadData();
  }
);

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
.segment {
  margin-bottom: 12px;
}
.filter {
  margin-bottom: 12px;
}
.pagination {
  margin-top: 12px;
  text-align: right;
}
</style>

