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

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function sizeToSlider(widthDp) {
  const scale = Number(widthDp || 140) / 140;
  return clamp((scale - 0.6) / 1.9, 0, 1);
}

function sliderToSize(value) {
  return Math.round(140 * (0.6 + clamp(Number(value), 0, 1) * 1.9));
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
  const [floating, setFloating] = ctx.useState("floating", {
    status: "hidden",
    widthDp: 140,
    heightDp: 140,
    alpha: 1,
    snapMode: "quarter",
    soundEnabled: true,
    soundVolume: 1,
    pressSoundResource: "sound_duck_press",
    releaseSoundResource: "sound_duck_release",
  });
  const [sizeDraft, setSizeDraft] = ctx.useState("sizeDraft", sizeToSlider(140));
  const [alphaDraft, setAlphaDraft] = ctx.useState("alphaDraft", 1);
  const [volumeDraft, setVolumeDraft] = ctx.useState("volumeDraft", 1);
  const [soundSet, setSoundSet] = ctx.useState("soundSet", "duck");

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
      const next = await ToolPkg.floatingWindow.show("whale", {});
      setFloating(next);
      setOverlayVisible(true);
    } catch (error) {
      console.error("[dsh-whale-widget] floating window show failed", error);
      setModel({ ...model, state: "error", error: "浮窗启动失败，请检查悬浮窗权限" });
    }
  }

  async function hideOverlay() {
    try {
      const next = await ToolPkg.floatingWindow.hide("whale");
      setFloating(next);
      setOverlayVisible(false);
    } catch (error) {
      console.error("[dsh-whale-widget] floating window hide failed", error);
    }
  }

  async function loadFloatingState() {
    try {
      const next = await ToolPkg.floatingWindow.get("whale");
      setFloating(next);
      setOverlayVisible(next.status === "visible");
      setSizeDraft(sizeToSlider(next.widthDp));
      setAlphaDraft(clamp(Number(next.alpha), 0.2, 1));
      setVolumeDraft(clamp(Number(next.soundVolume), 0, 1));
      setSoundSet(next.pressSoundResource === "sound_fx_press" ? "fx" : "duck");
    } catch (error) {
      console.error("[dsh-whale-widget] floating state load failed", error);
    }
  }

  async function updateFloating(patch) {
    try {
      const next = await ToolPkg.floatingWindow.update("whale", patch);
      setFloating(next);
    } catch (error) {
      console.error("[dsh-whale-widget] floating state update failed", error);
      setModel({ ...model, state: "error", error: "悬浮窗设置保存失败" });
    }
  }

  async function commitSize() {
    const sizeDp = sliderToSize(sizeDraft);
    await updateFloating({ widthDp: sizeDp, heightDp: sizeDp });
  }

  async function commitAlpha() {
    await updateFloating({ alpha: clamp(Number(alphaDraft), 0.2, 1) });
  }

  async function setSnapEnabled(enabled) {
    await updateFloating({ snapMode: enabled ? "quarter" : "none" });
  }

  async function setSoundEnabled(enabled) {
    await updateFloating({ soundEnabled: enabled });
  }

  async function commitVolume() {
    const volume = clamp(Number(volumeDraft), 0, 1);
    setFloating({ ...floating, soundVolume: volume, soundEnabled: volume > 0 });
    await updateFloating({ soundVolume: volume, soundEnabled: volume > 0 });
  }

  async function chooseSound(nextSet) {
    const isFx = nextSet === "fx";
    setSoundSet(nextSet);
    await updateFloating({
      pressSoundResource: isFx ? "sound_fx_press" : "sound_duck_press",
      releaseSoundResource: isFx ? "sound_fx_release" : "sound_duck_release",
    });
  }

  async function resetFloating() {
    setSizeDraft(sizeToSlider(140));
    setAlphaDraft(1);
    setVolumeDraft(1);
    setSoundSet("duck");
    await updateFloating({
      widthDp: 140,
      heightDp: 140,
      alpha: 1,
      snapMode: "quarter",
      soundEnabled: true,
      soundVolume: 1,
      pressSoundResource: "sound_duck_press",
      releaseSoundResource: "sound_duck_release",
    });
  }

  const onLoad = async () => {
    try {
      const path = await ToolPkg.readResource("whale_image", "whale.png");
      setImagePath(path);
    } catch (error) {
      console.error("[dsh-whale-widget] whale image load failed", error);
    }
    await refresh();
    await loadFloatingState();
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
      textCard(UI, colors, "悬浮窗设置", "调整大小、透明度、吸附和按压反馈"),
      UI.Row(
        { fillMaxWidth: true, horizontalArrangement: "spaceBetween", verticalAlignment: "center" },
        [
          UI.Text({ text: `大小 ${(0.6 + sizeDraft * 1.9).toFixed(1)}x`, style: "bodyMedium", color: colors.onSurface }),
          UI.Text({ text: `${floating.widthDp || 140}dp`, style: "bodySmall", color: colors.onSurfaceVariant }),
        ]
      ),
      UI.Slider({
        fillMaxWidth: true,
        value: sizeDraft,
        onValueChange: setSizeDraft,
        onValueChangeFinished: commitSize,
      }),
      UI.Row(
        { fillMaxWidth: true, horizontalArrangement: "spaceBetween", verticalAlignment: "center" },
        [
          UI.Text({ text: "透明度", style: "bodyMedium", color: colors.onSurface }),
          UI.Text({ text: `${Math.round(alphaDraft * 100)}%`, style: "bodySmall", color: colors.onSurfaceVariant }),
        ]
      ),
      UI.Slider({
        fillMaxWidth: true,
        value: (alphaDraft - 0.2) / 0.8,
        onValueChange: (value) => setAlphaDraft(0.2 + Number(value) * 0.8),
        onValueChangeFinished: commitAlpha,
      }),
      UI.Row(
        { fillMaxWidth: true, horizontalArrangement: "spaceBetween", verticalAlignment: "center" },
        [
          UI.Text({ text: "自动边缘吸附", style: "bodyMedium", color: colors.onSurface }),
          UI.Switch({ checked: floating.snapMode !== "none", onCheckedChange: setSnapEnabled }),
        ]
      ),
      UI.Row(
        { fillMaxWidth: true, spacing: 8 },
        [
          UI.Button({ weight: 1, text: "小黄鸭音效", enabled: soundSet !== "duck", onClick: () => chooseSound("duck") }),
          UI.Button({ weight: 1, text: "音效 1", enabled: soundSet !== "fx", onClick: () => chooseSound("fx") }),
        ]
      ),
      UI.Row(
        { fillMaxWidth: true, horizontalArrangement: "spaceBetween", verticalAlignment: "center" },
        [
          UI.Text({ text: "按压音效", style: "bodyMedium", color: colors.onSurface }),
          UI.Switch({ checked: floating.soundEnabled === true, onCheckedChange: setSoundEnabled }),
        ]
      ),
      UI.Row(
        { fillMaxWidth: true, horizontalArrangement: "spaceBetween", verticalAlignment: "center" },
        [
          UI.Text({ text: "音量", style: "bodyMedium", color: colors.onSurface }),
          UI.Text({ text: `${Math.round(volumeDraft * 100)}%`, style: "bodySmall", color: colors.onSurfaceVariant }),
        ]
      ),
      UI.Slider({ fillMaxWidth: true, value: volumeDraft, onValueChange: setVolumeDraft, onValueChangeFinished: commitVolume }),
      UI.Button({ fillMaxWidth: true, text: "恢复默认悬浮窗设置", onClick: resetFloating }),
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
