---
fork: https://github.com/luojiaping/Operit.git
scope: per-chat Agent activation, pre-Legacy route dispatch, and transient turn bridge
status: lifecycle_hardened_pending_device_e2e
implementation_commit: 286c41c04
build_commit: 37152bdc7
route_test_commit: 4e70c2077
hardening_commit: cfead4225
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

The lifecycle hardening pass also adds:

- a per-chat shared response stream for WebChat and other stream consumers
- turn IDs so stale completion/cancellation cannot clear a newer chat runtime
- tracked pre-route send jobs for immediate cancellation
- destructive-mutation cancellation through ChatServiceCore
- Legacy runtime history filtering for Agent-owned assistant messages
- current-chat guards around display-window reloads
- reuse of the latest open root session after deactivate/reactivate
- OpenAI provider/model and usage metrics on persisted Agent output
- monotonic Agent user/assistant timestamps
- a Hub activation toggle in the published Agent-style input without changing its existing send
  behavior

## 4. Current Limitations

- The activation toggle and coordinator tests are included in the hardened APK.
- Agent reasoning/usage is persisted in Agent step records but is not rendered as a dedicated UI
  section.
- Tools, permissions, attachments, group orchestration, automatic continuation, and production
  ToolPkg integration remain outside this stage.
- Device instrumentation remains deferred by request.

## 5. Verification

On the route implementation commit, the Agent/Kernel/Responses targeted JVM suite passed 34/34,
and `:app:compileDebugAndroidTestKotlin` passed. The lifecycle hardening source compiled and the
targeted suite plus coordinator tests passed 36/36. Remote `development` sync and `build_dev`
passed for `940c85554` before the hardening pass:

- artifact: `operit-dev-development-940c8555.apk`
- size: `403232415`
- SHA-256: `072b0320df37431be3d072c85c315795f60e0c65a9580be30dd81906d377a4d4`

The hardening pass still needs device E2E verification and production send-path integration
coverage before it can be called an MVP release candidate.

The previous hardened APK build passed for `792d30e99`:

- artifact: `operit-dev-development-792d30e9.apk`
- size: `403236511`
- SHA-256: `2370ea65359d79aab27cb26ca83db9a1a0ca95612b55bce821a135c224904b7f`

The hardened targeted JVM suite passed 36/36 on `cfead4225`; main compilation and Android test
source compilation passed. Final hardened APK build passed for `37152bdc7`:

- artifact: `operit-dev-development-37152bdc.apk`
- size: `403236511`
- SHA-256: `7f0c33bdd337478cdaf3bcaa65c4ab7a79d37e9172702a582debd03785db0bb0`

Device E2E and production send-path verification remain pending.
