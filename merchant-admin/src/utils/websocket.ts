import { ElNotification, ElMessage } from "element-plus";
import { useAuthStore } from "@/stores/auth";
import router from "@/router";

// 1 来单、2 催单、3 订单状态变更（对应后端 OrderServiceImpl.sendOrderMessage）
type WsMessageType = 1 | 2 | 3;

interface WsMessage {
  type: WsMessageType;
  orderId: number;
  content: string;
}

type MessageHandler = (message: WsMessage) => void;

// 服务端地址：默认与页面同源（生产由 nginx 反代 /ws，开发由 vite 代理），
// 需要指向别的地址时用环境变量 VITE_WS_BASE_URL 覆盖
const WS_BASE_URL =
  import.meta.env.VITE_WS_BASE_URL ||
  `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}`;

class WebSocketService {
  private ws: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private reconnectAttempts = 0;
  // 重连间隔指数退避并封顶，不做次数上限：
  // 到达上限就永久放弃，会让断线后的商家端再也收不到来单提醒
  private readonly baseReconnectDelay = 2000;
  private readonly maxReconnectDelay = 30000;
  private handlers: MessageHandler[] = [];
  private sid: string | null = null;

  connect(sid: string) {
    this.sid = sid;
    this.doConnect();
  }

  private doConnect() {
    // 先摘掉旧连接的回调再关闭，否则它的 onclose 会再触发一次重连
    this.closeCurrent();

    const token = useAuthStore().token;
    if (!token) {
      // 未登录（或已登出）时不再尝试连接
      console.warn("[WebSocket] 无令牌，跳过连接");
      return;
    }

    // 握手需携带管理端令牌，后端校验不通过会直接断开连接
    // 令牌通过子协议传递而不是拼在 URL 上：查询串会被代理与网关的访问日志记录下来
    this.ws = new WebSocket(`${WS_BASE_URL}/ws/${this.sid}`, token);

    this.ws.onopen = () => {
      console.log("[WebSocket] Connected");
      this.reconnectAttempts = 0;
    };

    this.ws.onmessage = (event) => {
      try {
        const message: WsMessage = JSON.parse(event.data);
        this.handleMessage(message);
      } catch (e) {
        console.error("[WebSocket] Parse message failed:", e);
      }
    };

    this.ws.onerror = (error) => {
      console.error("[WebSocket] Error:", error);
    };

    this.ws.onclose = () => {
      console.log("[WebSocket] Disconnected");
      this.attemptReconnect();
    };
  }

  private handleMessage(message: WsMessage) {
    if (message.type === 1) {
      showNewOrderNotification(message);
    } else if (message.type === 2) {
      showReminderNotification(message);
    }

    this.handlers.forEach((handler) => handler(message));
  }

  private attemptReconnect() {
    const delay = Math.min(
      this.baseReconnectDelay * 2 ** this.reconnectAttempts,
      this.maxReconnectDelay
    );
    this.reconnectAttempts++;
    console.log(`[WebSocket] ${delay / 1000}s 后重连（第 ${this.reconnectAttempts} 次）`);

    this.reconnectTimer = window.setTimeout(() => {
      this.doConnect();
    }, delay);
  }

  onMessage(handler: MessageHandler) {
    this.handlers.push(handler);
  }

  offMessage(handler: MessageHandler) {
    const index = this.handlers.indexOf(handler);
    if (index !== -1) {
      this.handlers.splice(index, 1);
    }
  }

  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    // 同样先摘回调：否则主动断开会被 onclose 当成掉线，又拉起一次重连
    this.closeCurrent();
    this.reconnectAttempts = 0;
    this.handlers = [];
    this.sid = null;
  }

  // 摘掉回调后再关闭连接，避免关闭动作触发重连
  private closeCurrent() {
    if (this.ws) {
      this.ws.onopen = null;
      this.ws.onmessage = null;
      this.ws.onerror = null;
      this.ws.onclose = null;
      this.ws.close();
      this.ws = null;
    }
  }
}

// 复用同一个 AudioContext：每次来单都 new 一个且不关闭会不断累积，浏览器对同时存在的上下文数量有上限
let audioContext: AudioContext | null = null;

function getAudioContext(): AudioContext {
  if (!audioContext) {
    const Ctor = window.AudioContext || (window as any).webkitAudioContext;
    audioContext = new Ctor();
  }
  return audioContext;
}

function playOrderSound() {
  try {
    const ctx = getAudioContext();
    // 浏览器的自动播放策略会把上下文挂起，播放前尝试恢复
    if (ctx.state === "suspended") {
      ctx.resume().catch(() => {});
    }

    const oscillator = ctx.createOscillator();
    const gainNode = ctx.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(ctx.destination);

    oscillator.type = "sine";
    oscillator.frequency.setValueAtTime(800, ctx.currentTime);
    oscillator.frequency.setValueAtTime(1000, ctx.currentTime + 0.15);

    gainNode.gain.setValueAtTime(0.3, ctx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.5);

    oscillator.start(ctx.currentTime);
    oscillator.stop(ctx.currentTime + 0.5);
  } catch (e) {
    console.warn("[WebSocket] Audio playback failed:", e);
  }
}

function showNewOrderNotification(message: WsMessage) {
  playOrderSound();

  ElNotification({
    title: "来单提醒",
    message: message.content,
    type: "success",
    duration: 0,
    onClick: () => {
      // 路由用的是 history 模式，改 location.hash 不会触发跳转
      router.push("/orders");
    },
  });

  ElMessage.success(`新订单: ${message.content}`);
}

function showReminderNotification(message: WsMessage) {
  ElNotification({
    title: "催单提醒",
    message: `${message.content} 催促尽快出餐`,
    type: "warning",
    duration: 5000,
  });

  ElMessage.warning(`${message.content} 催单`);
}

export const wsService = new WebSocketService();
export type { WsMessage, WsMessageType };
