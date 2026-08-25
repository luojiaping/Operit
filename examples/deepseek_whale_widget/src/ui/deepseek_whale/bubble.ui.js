const bridge = require("./bridge.js");

const BUBBLE_PATH = [
  { type: "moveTo", x: 0.806, y: 0.353 },
  { type: "cubicTo", x1: 0.806, y1: 0.536, x2: 0.644, y2: 0.684, x3: 0.443, y3: 0.684 },
  { type: "cubicTo", x1: 0.390, y1: 0.684, x2: 0.338, y2: 0.677, x3: 0.293, y3: 0.664 },
  { type: "cubicTo", x1: 0.219, y1: 0.621, x2: 0.079, y2: 0.511, x3: 0.079, y3: 0.353 },
  { type: "cubicTo", x1: 0.079, y1: 0.171, x2: 0.241, y2: 0.021, x3: 0.443, y3: 0.021 },
  { type: "cubicTo", x1: 0.644, y1: 0.021, x2: 0.806, y2: 0.171, x3: 0.806, y3: 0.353 },
  { type: "close" },
];

const BUBBLE_TAIL = [
  { type: "moveTo", x: 0.293, y: 0.664 },
  { type: "cubicTo", x1: 0.321, y1: 0.674, x2: 0.363, y2: 0.686, x3: 0.403, y3: 0.691 },
  { type: "cubicTo", x1: 0.388, y1: 0.718, x2: 0.350, y2: 0.722, x3: 0.319, y3: 0.704 },
  { type: "close" },
];

const RANDOM_LINES = [
  "好模型... ↓",
  "我去吃饭啦，测完叫我~",
  "压力一只蓝色大肥鱼？！",
  "DeepSleep...",
  "恭喜你实现 Token 自由！",
  "今天也要省着用哦~",
];

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function routeScale(ctx) {
  const moduleSpec = ctx.getModuleSpec();
  const routeArgs = JSON.parse(moduleSpec.routeArgsJson);
  return clamp(Number(routeArgs.scale), 0.6, 2.5);
}

function formatMoney(value, currency) {
  if (!value) return "--";
  return currency === "CNY" ? `¥ ${value}` : `${value} ${currency}`;
}

function bubbleCommands(scale) {
  return [
    { type: "drawPath", path: BUBBLE_PATH, unit: "fraction", color: "#FFFFFF", style: "fill" },
    { type: "drawPath", path: BUBBLE_PATH, unit: "fraction", color: "#203170", strokeWidth: 6 * scale, style: "stroke" },
    { type: "drawPath", path: BUBBLE_TAIL, unit: "fraction", color: "#FFFFFF", style: "fill" },
    { type: "drawPath", path: BUBBLE_TAIL, unit: "fraction", color: "#203170", strokeWidth: 6 * scale, style: "stroke" },
    { type: "circle", cx: 0.343, cy: 0.801, radius: 0.036, unit: "fraction", color: "#FFFFFF", filled: true },
    { type: "circle", cx: 0.343, cy: 0.801, radius: 0.036, unit: "fraction", color: "#203170", strokeWidth: 6 * scale, filled: false },
    { type: "circle", cx: 0.431, cy: 0.923, radius: 0.024, unit: "fraction", color: "#FFFFFF", filled: true },
    { type: "circle", cx: 0.431, cy: 0.923, radius: 0.024, unit: "fraction", color: "#203170", strokeWidth: 6 * scale, filled: false },
  ];
}

function Bubble(ctx) {
  const { UI } = ctx;
  const colors = ctx.MaterialTheme.colorScheme;
  const scale = routeScale(ctx);
  const [snapshot, setSnapshot] = ctx.useState("snapshot", {
    state: "loading",
    totalBalance: "",
    currency: "CNY",
    todayUsage: "",
    updatedAtMs: "",
  });
  const [gifPath, setGifPath] = ctx.useState("gifPath", "");
  const [bubbleMode, setBubbleMode] = ctx.useState("bubbleMode", "normal");
  const [randomMessage, setRandomMessage] = ctx.useState("randomMessage", "");
  const [randomIsGif, setRandomIsGif] = ctx.useState("randomIsGif", false);
  const [bubbleTimer, setBubbleTimer] = ctx.useMutable("bubbleTimer", null);

  async function load() {
    try {
      setSnapshot(await bridge.loadCachedModel());
      setGifPath(await ToolPkg.readResource("whale_gif", "rua.gif", true));
    } catch (error) {
      console.error("[dsh-whale-widget] bubble load failed", error);
      setSnapshot({
        state: "error",
        totalBalance: "",
        currency: "CNY",
        todayUsage: "",
        updatedAtMs: "",
      });
    }
  }

  async function hideBubble() {
    if (bubbleTimer !== null) clearTimeout(bubbleTimer);
    try {
      await ToolPkg.floatingWindow.hide("bubble");
    } catch (error) {
      console.error("[dsh-whale-widget] bubble hide failed", error);
    }
  }

  function scheduleClose() {
    if (bubbleTimer !== null) clearTimeout(bubbleTimer);
    setBubbleTimer(setTimeout(() => hideBubble(), 5000));
  }

  async function onLoad() {
    await load();
    scheduleClose();
  }

  function cycleBubble() {
    if (bubbleMode === "normal") {
      setRandomMessage(RANDOM_LINES[Math.floor(Math.random() * RANDOM_LINES.length)]);
      setRandomIsGif(Math.random() < 0.2);
      setBubbleMode("random");
      scheduleClose();
      return;
    }
    void hideBubble();
  }

  const amount = snapshot.totalBalance
    ? formatMoney(snapshot.totalBalance, snapshot.currency)
    : snapshot.state === "baseline"
      ? "已建立基线"
      : "--";
  const usage = snapshot.todayUsage
    ? `今日已用 ${formatMoney(snapshot.todayUsage, snapshot.currency)}`
    : snapshot.state === "empty"
      ? "打开设置页选择账户"
      : snapshot.state === "error"
        ? "余额读取失败"
        : "等待余额刷新";

  const bubbleText =
    bubbleMode === "random"
      ? UI.Text({
          text: randomMessage,
          fontSize: 14 * scale,
          color: "#203170",
          maxLines: 3,
          softWrap: true,
        })
      : UI.Column(
          {
            fillMaxSize: true,
            horizontalAlignment: "center",
            verticalArrangement: "center",
            spacing: scale,
          },
          [
            UI.Text({ text: "DeepSeek 余额", fontSize: 9 * scale, color: "#536BA9", maxLines: 1 }),
            UI.Text({ text: amount, fontSize: 20 * scale, color: "#203170", maxLines: 1 }),
            UI.Text({ text: usage, fontSize: 7 * scale, color: colors.onSurfaceVariant, maxLines: 2 }),
          ]
        );

  const bubbleContent =
    bubbleMode === "random" && randomIsGif && gifPath
      ? UI.Image({
          path: gifPath,
          width: 58 * scale,
          height: 45 * scale,
          contentScale: "fit",
          contentDescription: "",
        })
      : bubbleText;

  return UI.Box({ fillMaxSize: true, onLoad }, [
    UI.Canvas({ fillMaxSize: true, commands: bubbleCommands(scale) }),
    UI.Box(
      {
        width: 104 * scale,
        height: 76 * scale,
        modifier: ctx.Modifier.align("topStart").offset({ x: 18 * scale, y: 14 * scale }),
      },
      [bubbleContent]
    ),
    UI.Box({
      fillMaxSize: true,
      modifier: ctx.Modifier.align("topStart").clickable(cycleBubble),
    }),
  ]);
}

Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Bubble;
