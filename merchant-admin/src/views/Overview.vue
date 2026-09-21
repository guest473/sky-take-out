<template>
  <div class="page-card">
    <div class="top-row">
      <div>
        <h2 class="title">今日概览</h2>
      </div>
      <div class="shop-status">
        <span>营业状态：</span>
        <el-tag :type="shopStatus === 1 ? 'success' : 'info'">
          {{ shopStatus === 1 ? "营业中" : "打烊中" }}
        </el-tag>
        <el-switch
          v-if="auth.isManager"
          v-model="shopSwitch"
          :active-value="1"
          :inactive-value="0"
          @change="onShopChange"
        />
      </div>
    </div>
    <el-row :gutter="16" class="row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="label">今日营业额（元）</div>
          <div class="value">{{ business?.turnover ?? "—" }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="label">有效订单数</div>
          <div class="value">{{ business?.validOrderCount ?? "—" }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="label">订单完成率</div>
          <div class="value">
            {{ business?.orderCompletionRate != null ? (business.orderCompletionRate * 100).toFixed(0) + "%" : "—" }}
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="label">新增用户数</div>
          <div class="value">{{ business?.newUsers ?? "—" }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row align-bottom">
      <el-col :span="8">
        <div class="sub-card">
          <div class="sub-title">订单状态概览</div>
          <div class="sub-body">
            <div class="pill">
              待接单
              <span>{{ overview?.waitingOrders ?? 0 }}</span>
            </div>
            <div class="pill">
              待派送
              <span>{{ overview?.deliveredOrders ?? 0 }}</span>
            </div>
            <div class="pill">
              已完成
              <span>{{ overview?.completedOrders ?? 0 }}</span>
            </div>
            <div class="pill">
              已取消
              <span>{{ overview?.cancelledOrders ?? 0 }}</span>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="sub-card">
          <div class="sub-title">菜品总览</div>
          <div class="sub-body inline spacer">
            <span>在售：{{ dishOverview?.sold ?? 0 }}</span>
            <span>停售：{{ dishOverview?.discontinued ?? 0 }}</span>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="sub-card">
          <div class="sub-title">套餐总览</div>
          <div class="sub-body inline spacer">
            <span>在售：{{ setmealOverview?.sold ?? 0 }}</span>
            <span>停售：{{ setmealOverview?.discontinued ?? 0 }}</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="orders-block">
      <div class="orders-header">
        <div>
          <div class="orders-title">进行中的订单</div>
          <div class="orders-subtitle">展示除已完成和已取消外的最近订单。</div>
        </div>
      </div>
      <el-table :data="processingOrders" size="small" stripe>
        <el-table-column prop="number" label="订单号" min-width="160" />
        <el-table-column prop="userName" label="用户" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="amount" label="金额(元)" width="110" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            {{ statusText(row.status) }}
          </template>
        </el-table-column>
        <el-table-column prop="orderTime" label="下单时间" width="180" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 2"
              type="text"
              size="small"
              @click.stop="confirm(row)"
            >
              接单
            </el-button>
            <el-button
              v-if="row.status === 3"
              type="text"
              size="small"
              @click.stop="delivery(row)"
            >
              派单
            </el-button>
            <el-button
              v-if="row.status === 4"
              type="text"
              size="small"
              @click.stop="complete(row)"
            >
              完成
            </el-button>
            <el-button
              v-if="row.status === 2"
              type="text"
              size="small"
              @click.stop="rejection(row)"
            >
              拒单
            </el-button>
            <el-button
              v-if="[2, 3, 4].includes(row.status)"
              type="text"
              size="small"
              @click.stop="cancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { ElMessage, ElMessageBox, type MessageBoxInputData } from "element-plus";
import { wsService, WsMessage } from "@/utils/websocket";
import { useRefreshOnFocus } from "@/utils/refreshOnFocus";
import {
  fetchBusinessData,
  fetchOrderOverview,
  fetchDishOverview,
  fetchSetmealOverview,
  getShopStatus,
  setShopStatus,
  fetchOrders,
  confirmOrder,
  deliveryOrder,
  completeOrder,
  rejectOrder,
  cancelOrder
} from "@/api/admin";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();
const business = ref<any>();
const overview = ref<any>();
const dishOverview = ref<any>();
const setmealOverview = ref<any>();
const shopStatus = ref<number>(0);
const shopSwitch = ref<number>(0);
const processingOrders = ref<any[]>([]);

//概览指标：今日数据与订单总览，来单后会变化
const refreshSummary = async () => {
  const [biz, orderOverview] = await Promise.all([
    fetchBusinessData(),
    fetchOrderOverview()
  ]);
  business.value = biz;
  overview.value = orderOverview;
};

const loadAll = async () => {
  //各项数据互不依赖，并行请求，避免首屏串行等 5 个往返
  const [status] = await Promise.all([
    getShopStatus(),
    refreshSummary(),
    fetchDishOverview().then((data) => (dishOverview.value = data)),
    fetchSetmealOverview().then((data) => (setmealOverview.value = data)),
    refreshOrders()
  ]);
  shopStatus.value = status ?? 0;
  shopSwitch.value = shopStatus.value;
};

//来单提醒（type=1）与订单状态变更（type=3，其他终端处理订单）都要刷新概览
const handleWsMessage = (message: WsMessage) => {
  if (message.type === 1 || message.type === 3) {
    refreshSummary().catch(() => {});
    refreshOrders().catch(() => {});
  }
};

const onShopChange = async (val: number) => {
  await setShopStatus(val);
  shopStatus.value = val;
};

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

const refreshOrders = async () => {
  const pageParams = { page: 1, pageSize: 10, excludeStatus: "5,6" } as any;
  const pageData: any = await fetchOrders(pageParams);
  processingOrders.value = pageData.records || [];
};

const confirm = (row: any) => {
  ElMessageBox.confirm("确认接单？", "提示")
    .then(async () => {
      await confirmOrder({ id: row.id, status: 3 });
      ElMessage.success("已接单");
      refreshOrders();
    })
    .catch(() => {});
};

const delivery = (row: any) => {
  ElMessageBox.confirm("确认派单？", "提示")
    .then(async () => {
      await deliveryOrder(row.id);
      ElMessage.success("已派单");
      refreshOrders();
    })
    .catch(() => {});
};

const complete = (row: any) => {
  ElMessageBox.confirm("确认订单已完成？", "提示")
    .then(async () => {
      await completeOrder(row.id);
      ElMessage.success("订单已完成");
      refreshOrders();
    })
    .catch(() => {});
};

const rejection = (row: any) => {
  ElMessageBox.prompt("请输入拒单原因", "拒单")
    .then(async (res) => {
      const { value } = res as MessageBoxInputData;
      await rejectOrder({ id: row.id, rejectionReason: value });
      ElMessage.success("已拒单");
      refreshOrders();
    })
    .catch(() => {});
};

const cancel = (row: any) => {
  ElMessageBox.prompt("请输入取消原因", "取消订单")
    .then(async (res) => {
      const { value } = res as MessageBoxInputData;
      await cancelOrder({ id: row.id, cancelReason: value });
      ElMessage.success("已取消订单");
      refreshOrders();
    })
    .catch(() => {});
};

onMounted(() => {
  loadAll();
  wsService.onMessage(handleWsMessage);
});

//离开页面时解除订阅，避免重复注册
onUnmounted(() => {
  wsService.offMessage(handleWsMessage);
});

useRefreshOnFocus(loadAll);
</script>

<style scoped>
.title {
  margin: 0 0 4px;
}
.row {
  margin-bottom: 16px;
}
.row.align-bottom {
  display: flex;
  align-items: stretch;
}
.row.align-bottom .el-col {
  display: flex;
}
.row.align-bottom .sub-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.row.align-bottom .spacer {
  margin-top: auto;
}
.top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.shop-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #4b5563;
}
.stat-card {
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(145deg, #eff6ff, #ffffff);
  border: 1px solid #dbeafe;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
}
.label {
  font-size: 12px;
  color: #6b7280;
}
.value {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 600;
}
.sub-card {
  padding: 14px 16px;
  border-radius: 14px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
}
.sub-title {
  font-size: 13px;
  color: #111827;
  margin-bottom: 10px;
}
.sub-body {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.sub-body.inline {
  justify-content: space-between;
}
.pill {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  min-width: 120px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
}
.pill span {
  font-weight: 600;
}
.orders-block {
  margin-top: 16px;
}
.orders-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.orders-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.orders-subtitle {
  font-size: 12px;
  color: #6b7280;
}
</style>

