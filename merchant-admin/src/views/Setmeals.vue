<template>
  <div class="page-card">
    <div class="header">
      <div>
        <h2 class="title">套餐管理</h2>
      </div>
      <el-button type="primary" @click="openCreate">新增套餐</el-button>
    </div>

    <el-form :inline="true" :model="query" class="filter">
      <el-form-item label="名称">
        <el-input v-model="query.name" placeholder="套餐名称" clearable />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="query.categoryId" clearable placeholder="全部分类" style="width: 160px">
          <el-option
            v-for="c in categories"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
          <el-option label="在售" :value="1" />
          <el-option label="停售" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" size="small" stripe row-key="id" v-loading="loading">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="categoryName" label="分类" />
      <el-table-column prop="price" label="单价(元)" width="120" />
      <el-table-column prop="image" label="图片" width="120">
        <template #default="{ row }">
          <el-image
            v-if="row.image"
            :src="row.image"
            fit="cover"
            style="width: 64px; height: 40px; border-radius: 8px"
          />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? "在售" : "停售" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button type="text" @click="openEdit(row)">编辑</el-button>
          <el-button type="text" @click="toggleStatus(row)">
            {{ row.status === 1 ? "停售" : "设为在售" }}
          </el-button>
          <el-popconfirm title="确定删除该套餐吗？" @confirm="remove(row)">
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

    <el-dialog v-model="visible" :title="editing ? '编辑套餐' : '新增套餐'" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 220px">
            <el-option
              v-for="c in categories"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="单价(元)" prop="price">
          <el-input-number
            v-model="form.price"
            :min="0"
            :step="0.5"
            :precision="2"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="图片">
          <el-upload
            class="uploader"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="onUpload"
          >
            <img v-if="form.image" :src="form.image" class="preview" />
            <div v-else class="placeholder">点击上传</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="包含菜品" prop="setmealDishes">
          <div class="dish-picker">
            <div class="dish-toolbar">
              <el-select
                v-model="dishCategoryId"
                clearable
                placeholder="全部菜品分类"
                style="width: 200px"
                @change="loadDishes"
              >
                <el-option
                  v-for="c in dishCategories"
                  :key="c.id"
                  :label="c.name"
                  :value="c.id"
                />
              </el-select>
              <span class="chosen-count">已选 {{ form.setmealDishes.length }} 个菜品</span>
            </div>
            <div class="dish-list">
              <div v-for="d in dishes" :key="d.id" class="dish-item">
                <el-checkbox :model-value="isChosen(d.id)" @change="(val: unknown) => toggleDish(d, val)">
                  {{ d.name }}
                </el-checkbox>
                <span class="dish-price">￥{{ d.price }}</span>
                <el-input-number
                  :model-value="getCopies(d.id)"
                  :min="1"
                  :max="99"
                  size="small"
                  :disabled="!isChosen(d.id)"
                  controls-position="right"
                  style="width: 96px"
                  @change="(val: number | undefined) => setCopies(d.id, val)"
                />
              </div>
              <div v-if="!dishes.length" class="dish-empty">
                {{ dishCategoryId ? "该分类下暂无菜品" : "请先选择菜品分类" }}
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from "element-plus";
import {
  fetchSetmeals,
  createSetmeal,
  updateSetmeal,
  getSetmealById,
  deleteSetmeals,
  changeSetmealStatus,
  listCategoriesByType,
  listDishesByCategory,
  uploadFile
} from "@/api/admin";
import { showError } from "@/utils/error";

const list = ref<any[]>([]);
const total = ref(0);
const loading = ref(false);
const categories = ref<any[]>([]);
const dishCategories = ref<any[]>([]);
const dishes = ref<any[]>([]);
const dishCategoryId = ref<number | undefined>(undefined);

const query = reactive({
  page: 1,
  pageSize: 10,
  name: "",
  categoryId: undefined as number | undefined,
  status: undefined as number | undefined
});

const visible = ref(false);
const editing = ref(false);
const formRef = ref<FormInstance>();
const rules: FormRules = {
  name: [{ required: true, message: "请输入套餐名称", trigger: "blur" }],
  categoryId: [{ required: true, message: "请选择套餐分类", trigger: "change" }],
  price: [{ required: true, message: "请输入套餐单价", trigger: "blur" }],
  setmealDishes: [
    { required: true, type: "array", min: 1, message: "请至少添加一个菜品", trigger: "change" }
  ]
};
const form = reactive<any>({
  id: null,
  name: "",
  categoryId: undefined,
  price: 0,
  image: "",
  description: "",
  status: 1,
  setmealDishes: [] as any[]
});

const loadCategories = async () => {
  try {
    // 2 表示套餐分类
    categories.value = await listCategoriesByType(2);
  } catch (e) {
    showError(e, "加载分类失败");
  }
};

const loadDishCategories = async () => {
  try {
    // 1 表示菜品分类
    dishCategories.value = await listCategoriesByType(1);
  } catch (e) {
    showError(e, "加载菜品分类失败");
  }
};

const loadDishes = async () => {
  if (!dishCategoryId.value) {
    dishes.value = [];
    return;
  }
  try {
    dishes.value = (await listDishesByCategory(dishCategoryId.value)) || [];
  } catch (e) {
    dishes.value = [];
    showError(e, "加载菜品失败");
  }
};

const isChosen = (dishId: number) => form.setmealDishes.some((i: any) => i.dishId === dishId);

const getCopies = (dishId: number) =>
  form.setmealDishes.find((i: any) => i.dishId === dishId)?.copies ?? 1;

const toggleDish = (dish: any, checked: unknown) => {
  if (checked) {
    if (!isChosen(dish.id)) {
      form.setmealDishes.push({
        dishId: dish.id,
        name: dish.name,
        price: dish.price,
        copies: 1
      });
    }
  } else {
    form.setmealDishes = form.setmealDishes.filter((i: any) => i.dishId !== dish.id);
  }
};

const setCopies = (dishId: number, copies?: number) => {
  const item = form.setmealDishes.find((i: any) => i.dishId === dishId);
  if (item) {
    item.copies = Number(copies) || 1;
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const res: any = await fetchSetmeals(query);
    list.value = res.records || [];
    total.value = res.total || 0;
  } catch (e) {
    showError(e, "加载套餐失败");
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  editing.value = false;
  Object.assign(form, {
    id: null,
    name: "",
    categoryId: undefined,
    price: 0,
    image: "",
    description: "",
    status: 1,
    setmealDishes: []
  });
  dishCategoryId.value = dishCategories.value[0]?.id;
  loadDishes();
  visible.value = true;
};

const openEdit = async (row: any) => {
  try {
    const detail: any = await getSetmealById(row.id);
    editing.value = true;
    Object.assign(form, {
      id: detail.id,
      name: detail.name,
      categoryId: detail.categoryId,
      price: detail.price,
      image: detail.image,
      description: detail.description,
      status: detail.status,
      setmealDishes: (detail.setmealDishes || []).map((d: any) => ({
        dishId: d.dishId,
        name: d.name,
        price: d.price,
        copies: d.copies
      }))
    });
    dishCategoryId.value = dishCategories.value[0]?.id;
    loadDishes();
    visible.value = true;
  } catch (e) {
    showError(e, "加载套餐详情失败");
  }
};

const submit = async () => {
  if (!formRef.value) return;
  // 必填项与库中约束保持一致，避免提交后才由后端报错
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    try {
      if (editing.value) {
        await updateSetmeal(form);
        ElMessage.success("更新成功");
      } else {
        await createSetmeal(form);
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
      `确定要将该套餐${target === 1 ? "设为在售" : "停售"}吗？`,
      "提示"
    );
    await changeSetmealStatus(target, row.id);
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
    await deleteSetmeals([row.id]);
    ElMessage.success("已删除");
    loadData();
  } catch (e: any) {
    showError(e, "删除失败");
  }
};

//允许的图片类型与大小上限：后端也有同样的白名单与限制，这里提前拦住，省掉一次无用的请求
const ALLOWED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp"];
const MAX_UPLOAD_SIZE = 5 * 1024 * 1024;

const beforeUpload = (file: File) => {
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    ElMessage.error("只允许上传 jpg/png/gif/bmp/webp 格式的图片");
    return false;
  }
  if (file.size > MAX_UPLOAD_SIZE) {
    ElMessage.error("图片大小不能超过 5MB");
    return false;
  }
  return true;
};

const onUpload = async (options: UploadRequestOptions) => {
  try {
    const url = await uploadFile(options.file as File);
    form.image = url;
    ElMessage.success("上传成功");
    //只有成功才回调 onSuccess：放在 finally 里会让上传失败也被 el-upload 当成成功
    options.onSuccess({ url });
  } catch (e) {
    showError(e, "上传失败");
    //element-plus 未从根导出 UploadAjaxError 类型，这里取 onError 的形参类型
    options.onError(e as Parameters<UploadRequestOptions["onError"]>[0]);
  }
};

onMounted(() => {
  loadCategories();
  loadDishCategories();
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
.uploader {
  width: 120px;
  height: 80px;
}
.dish-picker {
  width: 100%;
}
.dish-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.chosen-count {
  font-size: 12px;
  color: #9ca3af;
}
.dish-list {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 10px;
  padding: 4px 10px;
}
.dish-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 0;
}
.dish-item .el-checkbox {
  flex: 1;
  min-width: 0;
}
.dish-price {
  font-size: 12px;
  color: #9ca3af;
  width: 60px;
  text-align: right;
}
.dish-empty {
  padding: 10px 0;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}
.preview,
.placeholder {
  width: 120px;
  height: 80px;
  border-radius: 12px;
  border: 1px dashed rgba(148, 163, 184, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 13px;
  overflow: hidden;
}
</style>

