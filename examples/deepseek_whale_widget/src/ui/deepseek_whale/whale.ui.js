function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function routeScale(ctx) {
  const moduleSpec = ctx.getModuleSpec();
  const routeArgs = JSON.parse(moduleSpec.routeArgsJson);
  return clamp(Number(routeArgs.scale), 0.6, 2.5);
}

async function openBubble(ctx) {
  try {
    await ToolPkg.floatingWindow.show("bubble", { scale: routeScale(ctx) });
  } catch (error) {
    console.error("[dsh-whale-widget] bubble show failed", error);
  }
}

function Whale(ctx) {
  const { UI } = ctx;
  const [imagePath, setImagePath] = ctx.useState("imagePath", "");

  async function load() {
    try {
      setImagePath(await ToolPkg.readResource("whale_image", "whale.png", true));
    } catch (error) {
      console.error("[dsh-whale-widget] whale image load failed", error);
    }
  }

  const whale = imagePath
    ? UI.Image({
        path: imagePath,
        fillMaxSize: true,
        contentScale: "fit",
        contentDescription: "DeepSeek whale",
      })
    : UI.Box({ fillMaxSize: true });

  return UI.Box(
    { fillMaxSize: true, onLoad: load },
    [
      UI.Box(
        {
          fillMaxSize: true,
          modifier: ctx.Modifier.clickable(() => openBubble(ctx)),
        },
        [whale]
      ),
    ]
  );
}

Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Whale;
