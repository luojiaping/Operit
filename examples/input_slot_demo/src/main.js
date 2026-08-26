const INPUT_SLOT_SCREEN = "dist/ui/input_slot/index.ui.js";

function renderAboveInput(event) {
  const payload = event.eventPayload;
  return {
    handled: true,
    composeDsl: {
      screen: INPUT_SLOT_SCREEN,
      state: {
        slot: payload.slot,
        chatId: payload.chatId,
        inputStyle: payload.inputStyle,
        isProcessing: payload.isProcessing === true,
      },
    },
  };
}

function renderInputDrawer(event) {
  const payload = event.eventPayload;
  return payload.isProcessing === true ? "Demo drawer: processing" : "Demo drawer";
}

function renderToolbarRight() {
  return {
    handled: true,
    text: "Slot",
  };
}

function registerToolPkg() {
  ToolPkg.registerInputSlotPlugin({
    id: "demo_above_input",
    slot: "above_input",
    function: renderAboveInput,
  });
  ToolPkg.registerInputSlotPlugin({
    id: "demo_input_drawer",
    slot: "input_drawer",
    function: renderInputDrawer,
  });
  ToolPkg.registerInputSlotPlugin({
    id: "demo_input_toolbar_right",
    slot: "input_toolbar_right",
    function: renderToolbarRight,
  });
  return true;
}

exports.registerToolPkg = registerToolPkg;
exports.renderAboveInput = renderAboveInput;
exports.renderInputDrawer = renderInputDrawer;
exports.renderToolbarRight = renderToolbarRight;
