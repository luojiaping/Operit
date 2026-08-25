const bridge = require("./bridge.js");

function formatMoney(value, currency) {
  if (!value) return "--";
  return currency === "CNY" ? `¥ ${value}` : `${value} ${currency}`;
}

function Overlay(ctx) {
  const { UI } = ctx;
  const colors = ctx.MaterialTheme.colorScheme;
  const [snapshot, setSnapshot] = ctx.useState("snapshot", {
    state: "loading",
    totalBalance: "",
    currency: "CNY",
    todayUsage: "",
    updatedAtMs: "",
  });
  const [imagePath, setImagePath] = ctx.useState("imagePath", "");

  async function load() {
    try {
      setSnapshot(await bridge.loadCachedModel());
      setImagePath(await ToolPkg.readResource("whale_image", "whale.png", true));
    } catch (error) {
      console.error("[dsh-whale-widget] overlay refresh failed", error);
      setSnapshot({
        state: "error",
        totalBalance: "",
        currency: "CNY",
        todayUsage: "",
        updatedAtMs: "",
      });
    }
  }

  const amount = snapshot.totalBalance
    ? formatMoney(snapshot.totalBalance, snapshot.currency)
    : snapshot.state === "baseline"
      ? "已建立基线"
      : "--";
  const usage = snapshot.todayUsage
    ? `今日已用 ${formatMoney(snapshot.todayUsage, snapshot.currency)}`
    : snapshot.usageMode === "platform" && snapshot.usageState === "credential_required"
      ? "平台用量需要配置 Token"
    : snapshot.usageMode === "platform" && snapshot.usageState === "error"
      ? "平台用量读取失败"
    : snapshot.state === "empty"
      ? "打开设置页选择账户"
      : "等待余额刷新";
  const latestTurn = snapshot.latestTurn;
  const turnCost =
    latestTurn && latestTurn.cost && latestTurn.cost.knownAmount
      ? `上一轮消耗 ${formatMoney(latestTurn.cost.knownAmount, latestTurn.cost.currency)}`
      : "暂无最近一轮费用";

  return UI.Card(
    {
      fillMaxSize: true,
      containerColor: "#F7F9FF",
      border: { width: 1, color: "#203170" },
      shape: { type: "rounded", cornerRadius: 18 },
      onLoad: load,
    },
    UI.Column(
      { fillMaxSize: true, padding: 14, spacing: 8 },
      [
        UI.Row(
          { fillMaxWidth: true, horizontalArrangement: "spaceBetween", verticalAlignment: "center" },
          [
            UI.Text({ text: "DeepSeek 余额", style: "titleMedium", color: "#203170" }),
            UI.IconButton({
              icon: "close",
              onClick: async () => {
                await ToolPkg.floatingWindow.hide("whale");
              },
            }),
          ]
        ),
        imagePath
          ? UI.Image({
              path: imagePath,
              height: 190,
              fillMaxWidth: true,
              contentDescription: "DeepSeek whale",
            })
          : UI.Spacer({ height: 80 }),
        UI.Text({ text: amount, style: "headlineMedium", color: "#203170" }),
        UI.Text({ text: usage, style: "bodyMedium", color: "#536BA9" }),
        UI.Text({ text: turnCost, style: "bodySmall", color: "#E0433F" }),
        UI.Text({
          text: snapshot.state === "baseline" ? "首次观测已建立余额基线" : "点击鲸鱼设置账户和用量模式",
          style: "bodySmall",
          color: colors.onSurfaceVariant,
        }),
      ]
    )
  );
}

Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Overlay;
