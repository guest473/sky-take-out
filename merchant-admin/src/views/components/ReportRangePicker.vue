<template>
  <div class="range">
    <el-date-picker
      v-model="inner"
      type="daterange"
      range-separator="至"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
      value-format="YYYY-MM-DD"
      @change="emitChange"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

// 使用字符串数组存储日期，格式为 YYYY-MM-DD
const model = defineModel<string[] | null>();

const inner = computed({
  get: () => model.value,
  set: (val) => {
    model.value = val as string[] | null;
  }
});

const emit = defineEmits<{ change: [] }>();

const emitChange = () => {
  emit("change");
};
</script>

<style scoped>
.range {
  margin: 12px 0;
}
</style>

