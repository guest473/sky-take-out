<template>
  <div class="page-card">
    <h2 class="title">数据报表</h2>

    <el-tabs v-model="active">
      <el-tab-pane label="营业额" name="turnover">
        <div class="tab-header">
          <ReportRangePicker v-model="range" @change="loadTurnover" />
          <el-button size="small" @click="exportTurnover">导出</el-button>
        </div>
        <div ref="turnoverRef" class="chart"></div>
      </el-tab-pane>
      <el-tab-pane label="用户" name="user">
        <div class="tab-header">
          <ReportRangePicker v-model="range" @change="loadUser" />
          <el-button size="small" @click="exportUser">导出</el-button>
        </div>
        <div ref="userRef" class="chart"></div>
      </el-tab-pane>
      <el-tab-pane label="订单" name="order">
        <div class="tab-header">
          <ReportRangePicker v-model="range" @change="loadOrder" />
          <el-button size="small" @click="exportOrder">导出</el-button>
        </div>
        <div ref="orderRef" class="chart"></div>
      </el-tab-pane>
      <el-tab-pane label="销量 Top10" name="top10">
        <div class="tab-header">
          <ReportRangePicker v-model="range" @change="loadTop10" />
          <el-button size="small" @click="exportTop10">导出</el-button>
        </div>
        <div ref="top10Ref" class="chart"></div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRefreshOnFocus } from "@/utils/refreshOnFocus";
import dayjs from "dayjs";
import * as echarts from "echarts";
import {
  fetchTurnoverReport,
  fetchUserReport,
  fetchOrderReport,
  fetchSalesTop10
} from "@/api/admin";
import ReportRangePicker from "@/views/components/ReportRangePicker.vue";

const active = ref("turnover");
// 与 ReportRangePicker 保持一致，使用 YYYY-MM-DD 字符串数组
const range = ref<string[] | null>([
  dayjs().subtract(7, "day").format("YYYY-MM-DD"),
  dayjs().format("YYYY-MM-DD")
]);

const turnoverRef = ref<HTMLDivElement | null>(null);
const userRef = ref<HTMLDivElement | null>(null);
const orderRef = ref<HTMLDivElement | null>(null);
const top10Ref = ref<HTMLDivElement | null>(null);

let turnoverChart: echarts.ECharts | null = null;
let userChart: echarts.ECharts | null = null;
let orderChart: echarts.ECharts | null = null;
let top10Chart: echarts.ECharts | null = null;

//窗口 resize 时重绘图表，200ms 内只执行一次
let resizeTimer: number | null = null;
const handleResize = () => {
  if (resizeTimer !== null) return;
  resizeTimer = window.setTimeout(() => {
    resizeTimer = null;
    [turnoverChart, userChart, orderChart, top10Chart].forEach((chart) => {
      if (chart) chart.resize();
    });
  }, 200);
};

const formatRange = () => {
  const [begin, end] = (range.value || []) as string[];
  return { begin, end };
};

const initChart = (el: HTMLDivElement | null, old: echarts.ECharts | null) => {
  if (!el) return old;
  if (old) {
    old.dispose();
  }
  return echarts.init(el);
};

//按标准 CSV 规则转义：含逗号、双引号或换行符时用双引号包裹，字段内双引号写成两个
const escapeCSVField = (value: string | number) => {
  const text = String(value);
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
};

const downloadCSV = (filename: string, header: string[], rows: (string | number)[][]) => {
  const lines = [
    header.map(escapeCSVField).join(","),
    ...rows.map((r) => r.map(escapeCSVField).join(","))
  ];
  //加 UTF-8 BOM，避免 Excel 打开含中文的 CSV 出现乱码
  const blob = new Blob([`\uFEFF${lines.join("\n")}`], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
};

const loadTurnover = async () => {
  const data = await fetchTurnoverReport(formatRange());
  await nextTick();
  turnoverChart = initChart(turnoverRef.value, turnoverChart);
  if (!turnoverChart) return;
  const dates = (data.dateList || "").split(",");
  const values = (data.turnoverList || "")
    .split(",")
    .filter(Boolean)
    .map((v: string) => Number(v));
  turnoverChart.setOption({
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: dates },
    yAxis: { type: "value", name: "营业额(元)" },
    series: [
      {
        type: "line",
        smooth: true,
        data: values,
        areaStyle: {}
      }
    ]
  });
};

const loadUser = async () => {
  const data = await fetchUserReport(formatRange());
  await nextTick();
  userChart = initChart(userRef.value, userChart);
  if (!userChart) return;
  const dates = (data.dateList || "").split(",");
  const total = (data.totalUserList || "")
    .split(",")
    .filter(Boolean)
    .map((v: string) => Number(v));
  const added = (data.newUserList || "")
    .split(",")
    .filter(Boolean)
    .map((v: string) => Number(v));
  userChart.setOption({
    tooltip: { trigger: "axis" },
    legend: { data: ["总用户数", "新增用户"] },
    xAxis: { type: "category", data: dates },
    yAxis: { type: "value" },
    series: [
      { name: "总用户数", type: "line", smooth: true, data: total },
      { name: "新增用户", type: "bar", data: added }
    ]
  });
};

const loadOrder = async () => {
  const data = await fetchOrderReport(formatRange());
  await nextTick();
  orderChart = initChart(orderRef.value, orderChart);
  if (!orderChart) return;
  const dates = (data.dateList || "").split(",");
  const orderCount = (data.orderCountList || "")
    .split(",")
    .filter(Boolean)
    .map((v: string) => Number(v));
  const validCount = (data.validOrderCountList || "")
    .split(",")
    .filter(Boolean)
    .map((v: string) => Number(v));
  orderChart.setOption({
    tooltip: { trigger: "axis" },
    legend: { data: ["订单数", "有效订单数"] },
    xAxis: { type: "category", data: dates },
    yAxis: { type: "value" },
    series: [
      { name: "订单数", type: "line", smooth: true, data: orderCount },
      { name: "有效订单数", type: "line", smooth: true, data: validCount }
    ]
  });
};

const loadTop10 = async () => {
  const data = await fetchSalesTop10(formatRange());
  await nextTick();
  top10Chart = initChart(top10Ref.value, top10Chart);
  if (!top10Chart) return;
  const names = (data.nameList || "").split(",");
  const numbers = (data.numberList || "")
    .split(",")
    .filter(Boolean)
    .map((v: string) => Number(v));
  top10Chart.setOption({
    tooltip: { trigger: "axis" },
    xAxis: { type: "value" },
    yAxis: { type: "category", data: names },
    series: [
      {
        type: "bar",
        data: numbers
      }
    ]
  });
};

const exportTurnover = async () => {
  const data = await fetchTurnoverReport(formatRange());
  const dates = (data.dateList || "").split(",");
  const values = (data.turnoverList || "").split(",");
  const rows = dates.map((d: string, i: number) => [d, values[i] || ""]);
  downloadCSV("turnover.csv", ["日期", "营业额"], rows);
};

const exportUser = async () => {
  const data = await fetchUserReport(formatRange());
  const dates = (data.dateList || "").split(",");
  const total = (data.totalUserList || "").split(",");
  const added = (data.newUserList || "").split(",");
  const rows = dates.map((d: string, i: number) => [d, total[i] || "", added[i] || ""]);
  downloadCSV("user.csv", ["日期", "用户总量", "新增用户"], rows);
};

const exportOrder = async () => {
  const data = await fetchOrderReport(formatRange());
  const dates = (data.dateList || "").split(",");
  const orderCount = (data.orderCountList || "").split(",");
  const validCount = (data.validOrderCountList || "").split(",");
  const rows = dates.map((d: string, i: number) => [
    d,
    orderCount[i] || "",
    validCount[i] || ""
  ]);
  downloadCSV("order.csv", ["日期", "订单数", "有效订单数"], rows);
};

const exportTop10 = async () => {
  const data = await fetchSalesTop10(formatRange());
  const names = (data.nameList || "").split(",");
  const numbers = (data.numberList || "").split(",");
  const rows = names.map((n: string, i: number) => [n, numbers[i] || ""]);
  downloadCSV("top10.csv", ["名称", "销量"], rows);
};

//加载当前页签对应的报表数据
const loadActive = () => {
  if (active.value === "turnover") loadTurnover();
  else if (active.value === "user") loadUser();
  else if (active.value === "order") loadOrder();
  else if (active.value === "top10") loadTop10();
};

onMounted(() => {
  loadActive();
  window.addEventListener("resize", handleResize);
});

watch(active, loadActive);

onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
  if (resizeTimer !== null) {
    window.clearTimeout(resizeTimer);
    resizeTimer = null;
  }
  turnoverChart?.dispose();
  userChart?.dispose();
  orderChart?.dispose();
  top10Chart?.dispose();
});

useRefreshOnFocus(loadActive);
</script>

<style scoped>
.title {
  margin: 0 0 12px;
}
.tab-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chart {
  margin-top: 12px;
  padding: 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  height: 320px;
}
</style>

