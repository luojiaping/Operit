const bridge = require("./bridge.js");

function textCard(UI, colors, title, body) {
  return UI.Card(
    {
      fillMaxWidth: true,
      containerColor: colors.surface,
      elevation: 1,
    },
    UI.Column(
      {
        fillMaxWidth: true,
        padding: 14,
        spacing: 6,
      },
      [
        UI.Text({ text: title, style: "titleMedium", color: colors.onSurface }),
        UI.Text({ text: body, style: "bodyMedium", color: colors.onSurfaceVariant }),
      ]
    )
  );
}

function formatMoney(value, currency) {
  if (!value) return "--";
  return currency === "CNY" ? `¥ ${value}` : `${value} ${currency}`;
}

function formatTokens(value) {
  if (typeof value !== "string" || value.trim() === "") return "--";
  return value.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

function accountCards(ctx, model, setModel, reload) {
  const { UI } = ctx;
  const colors = ctx.MaterialTheme.colorScheme;
  const accounts = Array.isArray(model.accounts) ? model.accounts : [];
  return accounts.map((account) => {
    const keys = Array.isArray(account.keys) ? account.keys : [];
    return UI.Card(
      {
        fillMaxWidth: true,
        containerColor: colors.surfaceVariant,
        elevation: 0,
      },
      UI.Column(
        { fillMaxWidth: true, padding: 12, spacing: 6 },
        [
          UI.Text({ text: account.name, style: "titleSmall", color: colors.onSurface }),
          UI.Text({ text: account.modelName || "DeepSeek", style: "bodySmall", color: colors.onSurfaceVariant }),
          ...keys.map((key) =>
            UI.Button({
              fillMaxWidth: true,
              enabled: key.enabled === true,
              text: `${key.name || key.id} · ${key.status}`,
              onClick: async () => {
                await bridge.saveSelection(ctx, account.configId, key.id);
                setModel({ ...model, configId: account.configId, keyId: key.id, state: "loading" });
                await reload();
              },
            })
          ),
        ]
      )
    );
  });
}

function buildTurnText(latestTurn) {
  if (!latestTurn || latestTurn.state !== "ready") return "暂无已完成回合";
  return [
    `${latestTurn.model || "DeepSeek"}`,
    `输入 ${formatTokens(latestTurn.totalInputTokens)} · 缓存 ${formatTokens(latestTurn.cachedInputTokens)}`,
    `输出 ${formatTokens(latestTurn.outputTokens)}`,
    `费用 ${formatMoney(latestTurn.cost && latestTurn.cost.knownAmount, latestTurn.cost && latestTurn.cost.currency)}`,
  ].join("\n");
}

function buildStatsText(stats) {
  if (!stats || stats.state !== "ready") return "暂无今日统计";
  const summary = stats.summary;
  if (!summary) return "暂无今日统计";
  return [
    `请求 ${formatTokens(summary.requests)}`,
    `总 Token ${formatTokens(summary.totalTokens && summary.totalTokens.knownSum)}`,
    `成本 ${formatMoney(summary.cost && summary.cost.knownAmount, summary.cost && summary.cost.currency)}`,
  ].join("\n");
}

function Screen(ctx) {
  const { UI } = ctx;
  const colors = ctx.MaterialTheme.colorScheme;
  const [model, setModel] = ctx.useState("model", {
    state: "loading",
    accounts: [],
    configId: bridge.envValue(bridge.CONFIG_KEY),
    keyId: bridge.envValue(bridge.KEY_KEY),
    usageMode: bridge.envValue(bridge.MODE_KEY) === "platform" ? "platform" : "ledger",
    balance: null,
    platform: null,
    stats: null,
    error: "",
  });
  const [platformToken, setPlatformToken] = ctx.useState("platformToken", "");
  const [imagePath, setImagePath] = ctx.useState("imagePath", "");
  const [overlayVisible, setOverlayVisible] = ctx.useState("overlayVisible", false);

  async function refresh() {
    try {
      setModel({ ...model, state: "loading", error: "" });
      const next = await bridge.loadModel();
      setModel(next);
    } catch (error) {
      console.error("[dsh-whale-widget] dashboard refresh failed", error);
      setModel({ ...model, state: "error", error: "宿主数据请求失败" });
    }
  }

  async function savePlatformToken() {
    try {
      await ToolPkg.host.call("deepseek.platform_set.v2", { token: platformToken });
      setPlatformToken("");
      await refresh();
    } catch (error) {
      console.error("[dsh-whale-widget] platform token save failed", error);
      setModel({ ...model, state: "error", error: "平台 Token 保存失败" });
    }
  }

  async function setUsageMode(value) {
    await Promise.resolve(ctx.setEnv(bridge.MODE_KEY, value));
    setModel({ ...model, usageMode: value });
    await refresh();
  }

  async function showOverlay() {
    try {
      await ToolPkg.floatingWindow.show("whale", {});
      setOverlayVisible(true);
    } catch (error) {
      console.error("[dsh-whale-widget] floating window show failed", error);
      setModel({ ...model, state: "error", error: "浮窗启动失败，请检查悬浮窗权限" });
    }
  }

  async function hideOverlay() {
    try {
      await ToolPkg.floatingWindow.hide("whale");
      setOverlayVisible(false);
    } catch (error) {
      console.error("[dsh-whale-widget] floating window hide failed", error);
    }
  }

  const onLoad = async () => {
    try {
      const path = await ToolPkg.readResource("whale_image", "whale.png");
      setImagePath(path);
    } catch (error) {
      console.error("[dsh-whale-widget] whale image load failed", error);
    }
    await refresh();
  };

  const balance = model.balance;
  const platform = model.platform;
  const modeText = model.usageMode === "platform" ? "平台实时用量" : "余额差值记账";
  const todayUsage =
    model.usageMode === "platform" && platform && platform.state === "ready"
      ? formatMoney(platform.amount, "CNY")
      : formatMoney(balance && balance.todayUsage, balance && balance.currency);

  return UI.LazyColumn(
    {
      fillMaxSize: true,
      padding: 16,
      spacing: 12,
      onLoad,
    },
    [
      UI.Row(
        { fillMaxWidth: true, horizontalArrangement: "spaceBetween", verticalAlignment: "center" },
        [
          UI.Column({ weight: 1, spacing: 4 }, [
            UI.Text({ text: "DeepSeek 鲸鱼余额", style: "headlineSmall", color: colors.onSurface }),
            UI.Text({ text: modeText, style: "bodyMedium", color: colors.onSurfaceVariant }),
          ]),
          UI.IconButton({ icon: "refresh", onClick: refresh, enabled: model.state !== "loading" }),
        ]
      ),
      imagePath
        ? UI.Image({ path: imagePath, height: 180, fillMaxWidth: true, contentDescription: "DeepSeek whale" })
        : UI.Spacer({ height: 12 }),
      textCard(
        UI,
        colors,
        "余额",
        balance && balance.state !== "empty"
          ? `${formatMoney(balance.totalBalance, balance.currency)}\n今日已用 ${todayUsage}`
          : model.state === "configuration_required"
            ? "先选择一个 DeepSeek 配置"
            : "正在读取余额"
      ),
      UI.Row(
        { fillMaxWidth: true, spacing: 8 },
        [
          UI.Button({
            weight: 1,
            text: "记账模式",
            enabled: model.usageMode !== "ledger",
            onClick: () => setUsageMode("ledger"),
          }),
          UI.Button({
            weight: 1,
            text: "平台用量",
            enabled: model.usageMode !== "platform",
            onClick: () => setUsageMode("platform"),
          }),
        ]
      ),
      UI.Row(
        { fillMaxWidth: true, spacing: 8 },
        [
          UI.Button({
            weight: 1,
            text: overlayVisible ? "浮窗运行中" : "显示浮窗",
            enabled: !overlayVisible,
            onClick: showOverlay,
          }),
          UI.Button({
            weight: 1,
            text: "隐藏浮窗",
            enabled: overlayVisible,
            onClick: hideOverlay,
          }),
        ]
      ),
      textCard(UI, colors, "今日 Token 统计", buildStatsText(model.stats)),
      textCard(UI, colors, "最近一轮", buildTurnText(model.stats && model.stats.latestTurn)),
      UI.TextField({
        fillMaxWidth: true,
        value: platformToken,
        onValueChange: setPlatformToken,
        label: "DeepSeek 平台 Token",
        placeholder: "仅在平台实时用量模式需要",
        isPassword: true,
        singleLine: true,
      }),
      UI.Button({
        fillMaxWidth: true,
        text: "保存平台 Token",
        enabled: platformToken.trim().length > 0,
        onClick: savePlatformToken,
      }),
      UI.Text({
        text: `平台凭据状态：${platform && platform.configured === true ? "已配置" : "未配置"}`,
        style: "bodySmall",
        color: colors.onSurfaceVariant,
      }),
      model.error
        ? UI.Text({ text: model.error, style: "bodyMedium", color: colors.error })
        : null,
      UI.Text({ text: "选择 DeepSeek 配置", style: "titleMedium", color: colors.onSurface }),
      ...accountCards(ctx, model, setModel, refresh),
    ]
  );
}

Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
