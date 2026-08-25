const PACKAGE_ID = "dsh-whale-widget";
const DASHBOARD_ROUTE = `toolpkg:${PACKAGE_ID}:ui:dashboard`;
const FLOATING_ROUTE = `toolpkg:${PACKAGE_ID}:ui:overlay`;

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
    id: "overlay",
    route: FLOATING_ROUTE,
    runtime: "compose_dsl",
    screen: "src/ui/deepseek_whale/overlay.ui.js",
    params: {},
    title: {
      zh: "鲸鱼悬浮窗",
      en: "Whale Overlay",
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
    contentRoute: FLOATING_ROUTE,
    title: {
      zh: "鲸鱼余额",
      en: "Whale Balance",
    },
    description: {
      zh: "长期驻留的 DeepSeek 余额和用量浮窗。",
      en: "A lightweight persistent DeepSeek balance and usage overlay.",
    },
    icon: "AccountBalanceWallet",
    widthDp: 300,
    heightDp: 520,
    draggable: true,
    resizable: true,
    refreshIntervalMs: 60000,
    onRefresh: refreshWhaleData,
  });

  return true;
}

exports.refreshWhaleData = refreshWhaleData;
exports.registerToolPkg = registerToolPkg;
