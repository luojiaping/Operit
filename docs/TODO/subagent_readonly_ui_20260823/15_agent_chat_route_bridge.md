---
fork: https://github.com/luojiaping/Operit.git
scope: per-chat Agent activation, pre-Legacy route dispatch, and transient turn bridge
status: build_verified_pending_route_ui_tests
implementation_commit: 286c41c04
build_commit: 940c85554
---

# Agent Chat Route Bridge

This stage connects an explicitly bound Agent chat to the existing send/cancel lifecycle while
leaving unbound chats on LegacyPipeline.

## 1. Activation

`AgentChatTurnCoordinator.activateAgentForChat(chatId)` creates the built-in text-only primary
session and binds it as the chat root. `ChatServiceCore` and `ChatViewModel` expose activation and
deactivation methods for the next UI control.

The binding is per chat. Deactivation clears the active root binding and cancels an in-flight Agent
job without deleting the retained session history.

## 2. Route Boundary

`MessageCoordinationDelegate.sendMessageInternal()` is now suspendable and resolves the route after
chat selection but before group orchestration, summary handling, role/model Legacy side effects,
or Legacy user-message persistence.

Bound Agent routes accept only:

- `PromptFunctionType.CHAT`
- persisted text turns
- no attachments
- no proxy sender
- no hidden user message
- no continuation or group orchestration

They use the explicit model override, fixed role-card model binding, or the global CHAT mapping,
then require the strict OpenAI Responses resolver. Permissions and tools are fixed to empty JSON
snapshots. Unsupported provider/configuration errors are surfaced as Agent input errors.

Unbound chats continue through the existing Legacy path unchanged.

## 3. Turn Bridge

`AgentChatTurnCoordinator` owns one Job per chat and maps typed Kernel events to the existing chat
runtime:

- text deltas update an `isVariantPreview` transient assistant message
- committed output reloads persisted chat history
- completion/failure updates input state
- cancellation uses the Kernel settlement path and reloads transient state under
  `NonCancellable`
- ChatServiceCore and ChatViewModel cancellation paths cancel Agent jobs as well as Legacy jobs

The transient preview is never sent through the Legacy persistence path. The Repository remains the
only writer for Agent-owned user/output messages.

## 4. Current Limitations

- No user-visible activation control has been added yet.
- No route-bridge JVM integration test exists yet.
- Agent reasoning/usage is persisted in Agent step records but is not rendered as a dedicated UI
  section.
- Tools, permissions, attachments, group orchestration, automatic continuation, and production
  ToolPkg integration remain outside this stage.
- Device instrumentation remains deferred by request.

## 5. Verification

On the route implementation commit, the Agent/Kernel/Responses targeted JVM suite passed 34/34,
and `:app:compileDebugAndroidTestKotlin` passed. Remote `development` sync and `build_dev` also
passed for `940c85554`:

- artifact: `operit-dev-development-940c8555.apk`
- size: `403232415`
- SHA-256: `072b0320df37431be3d072c85c315795f60e0c65a9580be30dd81906d377a4d4`

The route bridge still needs route-specific integration tests and a user-visible activation
control before it can be called an MVP release candidate.
