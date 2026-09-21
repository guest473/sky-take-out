import { onMounted, onUnmounted } from "vue";

//窗口重新可见/重新获得焦点时刷新数据：
//切到别的窗口或应用一段时间后回到本页，看到的是最新数据，而不是离开时的旧数据
export function useRefreshOnFocus(refresh: () => void) {
  let timer: number | null = null;

  const trigger = () => {
    //切走（不可见）时不刷新
    if (document.visibilityState === "hidden") {
      return;
    }
    //focus 与 visibilitychange 常成对触发，合并成一次，避免重复请求
    if (timer !== null) {
      return;
    }
    timer = window.setTimeout(() => {
      timer = null;
      //刷新失败（比如后端刚重启）不影响用户继续操作本页
      Promise.resolve(refresh()).catch(() => {});
    }, 200);
  };

  onMounted(() => {
    window.addEventListener("focus", trigger);
    document.addEventListener("visibilitychange", trigger);
  });

  onUnmounted(() => {
    window.removeEventListener("focus", trigger);
    document.removeEventListener("visibilitychange", trigger);
    if (timer !== null) {
      window.clearTimeout(timer);
      timer = null;
    }
  });
}
