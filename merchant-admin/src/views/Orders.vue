<template>
  <div class="page-card">
    <h2 class="title">订单管理</h2>

    <el-form :inline="true" :model="query" class="filter">
      <el-form-item label="订单号">
        <el-input v-model="query.number" placeholder="订单号" clearable />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="query.phone" placeholder="用户手机号" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="待付款" :value="1" />
          <el-option label="待接单" :value="2" />
          <el-option label="已接单" :value="3" />
          <el-option label="派送中" :value="4" />
          <el-option label="已完成" :value="5" />
          <el-option label="已取消" :value="6" />
        </el-select>
      </el-form-item>
      <el-form-item label="下单时间">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" size="small" stripe row-key="id" v-loading="loading" @row-click="onRowClick">
      <el-table-column prop="number" label="订单号" min-width="180" />
      <el-table-column prop="userName" label="用户" width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="amount" label="金额(元)" width="120" />
      <el-table-column prop="status" label="订单状态" width="120">
        <template #default="{ row }">
          {{ statusText(row.status) }}
        </template>
      </el-table-column>
      <el-table-column prop="orderTime" label="下单时间" width="180" />
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button type="text" @click.stop="showDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 2"
            type="text"
            @click.stop="confirm(row)"
          >
            接单
          </el-button>
          <el-button
            v-if="row.status === 3"
            type="text"
            @click.stop="delivery(row)"
          >
            派单
          </el-button>
          <el-button
            v-if="row.status === 4"
            type="text"
            @click.stop="complete(row)"
          >
            完成
          </el-button>
          <el-button
            v-if="row.status === 2"
            type="text"
            @click.stop="rejection(row)"
          >
            拒单
          </el-button>
          <el-button
            v-if="[2, 3, 4].includes(row.status)"
            type="text"
            @click.stop="cancel(row)"
          >
            取消
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

    <el-drawer v-model="detailVisible" title="订单详情" size="50%">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="订单号">{{ detail.number }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ detail.userName }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
        <el-descriptions-item label="金额(元)">{{ detail.amount }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ detail.orderTime }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">
          {{ detail.address }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          {{ detail.remark }}
        </el-descriptions-item>
      </el-descriptions>
      <h4 style="margin: 16px 0 8px">菜品明细</h4>
      <el-table :data="detail?.orderDetailList || []" size="small" border>
        <el-table-column prop="name" label="菜品" />
        <el-table-column prop="number" label="数量" width="80" />
        <el-table-column prop="amount" label="小计(元)" width="120" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch, onBeforeUnmount } from "vue";
import { ElMessage, ElMessageBox, type MessageBoxInputData } from "element-plus";
import {
  fetchOrders,
  getOrderDetail,
  confirmOrder,
  deliveryOrder,
  completeOrder,
  rejectOrder,
  cancelOrder
} from "@/api/admin";
import { wsService, WsMessage } from "@/utils/websocket";
import { useRefreshOnFocus } from "@/utils/refreshOnFocus";
import { showError } from "@/utils/error";

const list = ref<any[]>([]);
const total = ref(0);
const loading = ref(false);

const query = reactive<{
  page: number;
  pageSize: number;
  number?: string;
  phone?: string;
  status?: number;
  beginTime?: string;
  endTime?: string;
}>({
  page: 1,
  pageSize: 10
});

const dateRange = ref<[string, string] | null>(null);

watch(dateRange, (val) => {
  if (val && val.length === 2) {
    query.beginTime = val[0];
    query.endTime = val[1];
  } else {
    query.beginTime = undefined;
    query.endTime = undefined;
  }
});

const detailVisible = ref(false);
const detail = ref<any>(null);

const statusText = (status: number) => {
  switch (status) {
    case 1:
      return "待付款";
    case 2:
      return "待接单";
    case 3:
      return "已接单";
    case 4:
      return "派送中";
    case 5:
      return "已完成";
    case 6:
      return "已取消";
    default:
      return "未知";
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const res: any = await fetchOrders(query);
    list.value = res.records || [];
    total.value = res.total || 0;
  } catch (e) {
    showError(e, "加载订单失败");
  } finally {
    loading.value = false;
  }
};

const onRowClick = (row: any) => {
  showDetail(row);
};

const showDetail = async (row: any) => {
  try {
    detail.value = await getOrderDetail(row.id);
    detailVisible.value = true;
  } catch (e) {
    showError(e, "加载订单详情失败");
  }
};

const confirm = (row: any) => {
  ElMessageBox.confirm("确认接单？", "提示")
    .then(async () => {
      await confirmOrder({ id: row.id, status: 3 });
      ElMessage.success("已接单");
      loadData();
    })
    .catch(() => {});
};

const delivery = (row: any) => {
  ElMessageBox.confirm("确认派单？", "提示")
    .then(async () => {
      await deliveryOrder(row.id);
      ElMessage.success("已派单");
      loadData();
    })
    .catch(() => {});
};

const complete = (row: any) => {
  ElMessageBox.confirm("确认订单已完成？", "提示")
    .then(async () => {
      await completeOrder(row.id);
      ElMessage.success("订单已完成");
      loadData();
    })
    .catch(() => {});
};

const rejection = (row: any) => {
  ElMessageBox.prompt("请输入拒单原因", "拒单")
    .then(async (res) => {
      const { value } = res as MessageBoxInputData;
      await rejectOrder({ id: row.id, rejectionReason: value });
      ElMessage.success("已拒单");
      loadData();
    })
    .catch(() => {});
};

const cancel = (row: any) => {
  ElMessageBox.prompt("请输入取消原因", "取消订单")
    .then(async (res) => {
      const { value } = res as MessageBoxInputData;
      await cancelOrder({ id: row.id, cancelReason: value });
      ElMessage.success("已取消订单");
      loadData();
    })
    .catch(() => {});
};

//来单（1）、催单（2）、订单状态变更（3，其他终端处理订单）都刷新列表
const handleWsMessage = (message: WsMessage) => {
  if (message.type === 1 || message.type === 2 || message.type === 3) {
    loadData();
  }
};

onMounted(() => {
  loadData();
  wsService.onMessage(handleWsMessage);
});

onBeforeUnmount(() => {
  wsService.offMessage(handleWsMessage);
});

useRefreshOnFocus(loadData);
</script>

<style scoped>
.title {
  margin: 0 0 12px;
}
.filter {
  margin-bottom: 12px;
}
.pagination {
  margin-top: 12px;
  text-align: right;
}
</style>

