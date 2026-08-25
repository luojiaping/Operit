const PACKAGE_ID = "dsh-whale-widget";
const DASHBOARD_ROUTE = `toolpkg:${PACKAGE_ID}:ui:dashboard`;
const WHALE_ROUTE = `toolpkg:${PACKAGE_ID}:ui:whale`;
const BUBBLE_ROUTE = `toolpkg:${PACKAGE_ID}:ui:bubble`;

function configuredValue(key) {
  return getEnv(key) ?? "";
}

async function refreshWhaleData() {
  const configId = configuredValue("DEEPSEEK_WHALE_CONFIG_ID");
  const keyId = configuredValue("DEEPSEEK_WHALE_KEY_ID");
  if (!configId || !keyId) {
    return { state: "configuration_required" };
  }
  return await ToolPkg.host.call("deepseek.balance.v2", {
    configId,
    keyId,
    currency: "CNY",
    timezone: configuredValue("DEEPSEEK_WHALE_TIMEZONE"),
    date: configuredValue("DEEPSEEK_WHALE_DATE"),
    usageMode: configuredValue("DEEPSEEK_WHALE_USAGE_MODE"),
  });
}

function registerToolPkg() {
  ToolPkg.registerUiRoute({
    id: "dashboard",
    route: DASHBOARD_ROUTE,
    runtime: "compose_dsl",
    screen: "src/ui/deepseek_whale/index.ui.js",
    params: {},
    title: {
      zh: "鲸鱼余额",
      en: "Whale Balance",
    },
  });

  ToolPkg.registerUiRoute({
    id: "whale",
    route: WHALE_ROUTE,
    runtime: "compose_dsl",
    screen: "src/ui/deepseek_whale/whale.ui.js",
    params: {},
    title: {
      zh: "鲸鱼悬浮窗",
      en: "Whale Overlay",
    },
  });

  ToolPkg.registerUiRoute({
    id: "bubble",
    route: BUBBLE_ROUTE,
    runtime: "compose_dsl",
    screen: "src/ui/deepseek_whale/bubble.ui.js",
    params: {},
    title: {
      zh: "鲸鱼气泡",
      en: "Whale Bubble",
    },
  });

  ToolPkg.registerNavigationEntry({
    id: "dashboard_sidebar",
    route: DASHBOARD_ROUTE,
    surface: "main_sidebar_plugins",
    title: {
      zh: "鲸鱼余额",
      en: "Whale Balance",
    },
    icon: "AccountBalanceWallet",
    order: 115,
  });

  ToolPkg.registerFloatingWindow({
    id: "whale",
    contentRoute: WHALE_ROUTE,
    title: {
      zh: "鲸鱼余额",
      en: "Whale Balance",
    },
    description: {
      zh: "长期驻留的 DeepSeek 余额和用量浮窗。",
      en: "A lightweight persistent DeepSeek balance and usage overlay.",
    },
    icon: "AccountBalanceWallet",
    widthDp: 140,
    heightDp: 140,
    draggable: true,
    resizable: false,
    snapMode: "quarter",
    pressSoundResource: "sound_duck_press",
    releaseSoundResource: "sound_duck_release",
    refreshIntervalMs: 60000,
    onRefresh: refreshWhaleData,
  });

  ToolPkg.registerFloatingWindow({
    id: "bubble",
    contentRoute: BUBBLE_ROUTE,
    title: {
      zh: "鲸鱼气泡",
      en: "Whale Bubble",
    },
    description: {
      zh: "跟随鲸鱼显示余额和随机台词。",
      en: "A separate bubble that follows the whale overlay.",
    },
    widthDp: 140,
    heightDp: 140,
    draggable: false,
    resizable: false,
    snapMode: "none",
    followWindowId: "whale",
    followPlacement: "above",
    followGapDp: 8,
    refreshIntervalMs: 0,
  });

  return true;
}

exports.refreshWhaleData = refreshWhaleData;
exports.registerToolPkg = registerToolPkg;
