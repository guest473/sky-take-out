import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      "/admin": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      // WebSocket 同样代理到后端，前端才能用与页面同源的地址连接
      "/ws": {
        target: "ws://localhost:8080",
        ws: true
      }
    }
  },
  resolve: {
    alias: {
      "@": "/src"
    }
  }
});

