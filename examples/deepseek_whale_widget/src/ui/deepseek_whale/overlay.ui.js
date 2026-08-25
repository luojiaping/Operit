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

function formatMoney(value, currency) {
  if (!value) return "--";
  return currency === "CNY" ? `¥ ${value}` : `${value} ${currency}`;
}

function bubbleCommands(amount, usage, colors) {
  return [
    { type: "drawPath", path: BUBBLE_PATH, unit: "fraction", color: "#FFFFFF", style: "fill" },
    { type: "drawPath", path: BUBBLE_PATH, unit: "fraction", color: "#203170", strokeWidth: 6, style: "stroke" },
    { type: "drawPath", path: BUBBLE_TAIL, unit: "fraction", color: "#FFFFFF", style: "fill" },
    { type: "drawPath", path: BUBBLE_TAIL, unit: "fraction", color: "#203170", strokeWidth: 6, style: "stroke" },
    { type: "circle", cx: 0.343, cy: 0.801, radius: 0.036, unit: "fraction", color: "#FFFFFF", filled: true },
    { type: "circle", cx: 0.343, cy: 0.801, radius: 0.036, unit: "fraction", color: "#203170", strokeWidth: 6, filled: false },
    { type: "circle", cx: 0.431, cy: 0.923, radius: 0.024, unit: "fraction", color: "#FFFFFF", filled: true },
    { type: "circle", cx: 0.431, cy: 0.923, radius: 0.024, unit: "fraction", color: "#203170", strokeWidth: 6, filled: false },
    { type: "drawText", text: "DeepSeek 余额", x: 0.145, y: 0.255, unit: "fraction", color: "#536BA9", fontSize: 10, maxLines: 1 },
    { type: "drawText", text: amount, x: 0.145, y: 0.385, unit: "fraction", color: "#203170", fontSize: 20, maxLines: 1 },
    { type: "drawText", text: usage, x: 0.145, y: 0.560, unit: "fraction", color: colors.onSurfaceVariant, fontSize: 8, maxWidth: { value: 0.62, unit: "fraction" }, maxLines: 2 },
  ];
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
  const [bubbleVisible, setBubbleVisible] = ctx.useState("bubbleVisible", false);
  let bubbleTimer = null;

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

  function closeBubble() {
    if (bubbleTimer !== null) {
      clearTimeout(bubbleTimer);
      bubbleTimer = null;
    }
    setBubbleVisible(false);
  }

  async function openBubble() {
    if (bubbleTimer !== null) clearTimeout(bubbleTimer);
    setBubbleVisible(true);
    await load();
    bubbleTimer = setTimeout(closeBubble, 5000);
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

  const whale = imagePath
    ? UI.Image({
        path: imagePath,
        fillMaxSize: true,
        contentScale: "fit",
        contentDescription: "DeepSeek whale",
      })
    : UI.Spacer({ width: 84, height: 84 });

  return UI.Box(
    { fillMaxSize: true, onLoad: load },
    [
      bubbleVisible
        ? UI.Canvas({
            fillMaxSize: true,
            commands: bubbleCommands(amount, usage, colors),
          })
        : null,
      bubbleVisible
        ? UI.Box({
            width: 128,
            height: 100,
            modifier: ctx.Modifier.align("topStart").clickable(closeBubble),
          })
        : null,
      UI.Box(
        {
          width: 84,
          height: 84,
          modifier: ctx.Modifier.align("bottomEnd").clickable(openBubble),
        },
        [whale]
      ),
    ]
  );
}

Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Overlay;
