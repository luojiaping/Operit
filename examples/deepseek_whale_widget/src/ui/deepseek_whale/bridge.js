const CONFIG_KEY = "DEEPSEEK_WHALE_CONFIG_ID";
const KEY_KEY = "DEEPSEEK_WHALE_KEY_ID";
const MODE_KEY = "DEEPSEEK_WHALE_USAGE_MODE";

function envValue(key) {
  return getEnv(key) ?? "";
}

function dateParts(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function dayBounds(date) {
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const end = new Date(date.getFullYear(), date.getMonth(), date.getDate() + 1);
  return {
    date: dateParts(start),
    startSeconds: String(Math.floor(start.getTime() / 1000)),
    endSeconds: String(Math.floor(end.getTime() / 1000)),
    timezoneOffsetSeconds: String(-start.getTimezoneOffset() * 60),
    timezone: "",
  };
}

async function callHost(capability, payload) {
  return await ToolPkg.host.call(capability, payload);
}

async function loadModel() {
  const now = new Date();
  const bounds = dayBounds(now);
  const configId = envValue(CONFIG_KEY);
  const keyId = envValue(KEY_KEY);
  const usageMode = envValue(MODE_KEY) === "platform" ? "platform" : "ledger";
  const accounts = await callHost("deepseek.accounts.v2", {});
  const model = {
    state: configId && keyId ? "loading" : "configuration_required",
    accounts: accounts.accounts,
    configId,
    keyId,
    usageMode,
    balance: null,
    platform: null,
    stats: null,
    error: "",
  };
  if (!configId || !keyId) {
    return model;
  }

  const balance = await callHost("deepseek.balance.v2", {
    configId,
    keyId,
    currency: "CNY",
    date: bounds.date,
    timezone: bounds.timezone,
    usageMode,
  });
  const stats = await callHost("deepseek.stats.v2", {
    date: bounds.date,
    timezone: bounds.timezone,
  });
  const platformStatus = await callHost("deepseek.platform_status.v2", {});
  let platform = platformStatus;
  if (usageMode === "platform" && platformStatus.configured === true) {
    platform = await callHost("deepseek.platform_usage.v2", bounds);
  }
  model.state = balance.state;
  model.balance = balance;
  model.platform = platform;
  model.stats = stats;
  return model;
}

async function loadCachedModel() {
  const configId = envValue(CONFIG_KEY);
  const keyId = envValue(KEY_KEY);
  if (!configId || !keyId) {
    return {
      state: "configuration_required",
      totalBalance: "",
      currency: "CNY",
      todayUsage: "",
      updatedAtMs: "",
    };
  }
  return await callHost("deepseek.cached_snapshot.v2", { configId, keyId });
}

async function saveSelection(ctx, configId, keyId) {
  await Promise.resolve(ctx.setEnv(CONFIG_KEY, configId));
  await Promise.resolve(ctx.setEnv(KEY_KEY, keyId));
}

module.exports = {
  CONFIG_KEY,
  KEY_KEY,
  MODE_KEY,
  envValue,
  loadModel,
  loadCachedModel,
  saveSelection,
};
