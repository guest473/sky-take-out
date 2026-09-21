/// <reference types="vite/client" />

// 让 TS 认识 .vue 单文件组件（vue-tsc 需要，否则所有 import xxx from "*.vue" 都报 TS2307）
declare module "*.vue" {
  import type { DefineComponent } from "vue";
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
