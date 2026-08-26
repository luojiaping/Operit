function SlotScreen(ctx) {
  const { UI } = ctx;
  return UI.Card(
    {
      fillMaxWidth: true,
      containerColor: "#E7EAF7",
      elevation: 0,
    },
    UI.Column(
      {
        fillMaxWidth: true,
        padding: 8,
        spacing: 2,
      },
      [
        UI.Text({
          text: "Input slot Compose DSL",
          style: "labelLarge",
          color: "#203170",
        }),
        UI.Text({
          text: "Rendered above the chat input",
          style: "bodySmall",
          color: "#536BA9",
        }),
      ]
    )
  );
}

Object.defineProperty(exports, "__esModule", { value: true });
exports.default = SlotScreen;
