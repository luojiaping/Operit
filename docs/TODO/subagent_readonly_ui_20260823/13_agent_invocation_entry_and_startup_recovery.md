---
fork: https://github.com/luojiaping/Operit.git
scope: internal Agent invocation entry and process-start recovery
status: build_jvm_android_source_verified_pending_instrumentation
base_commit: 3615e37c0
implementation_commit: d87481a78
---

# Internal Agent Invocation Entry

This stage adds the host-side entry needed to invoke the existing text-only AgentKernel. It does
not connect MessageCoordinationDelegate, EnhancedAIService, ToolPkg, ChatView, or production
AgentRouter dispatch.

## 1. Runtime Startup Recovery

`core/agent/runtime/AgentRuntimeStartupCoordinator.kt` owns one process-start recovery pass.

- `start(scope)` launches `AgentExecutionRepository.recoverInterruptedRuns()` on IO.
- `awaitReady()` blocks an Agent invocation until recovery has completed.
- Recovery failure completes readiness exceptionally and keeps invocation unavailable.
- A second `start()` call is rejected to preserve one recovery owner per process.
- Cancellation completes readiness as cancelled and propagates through the startup job.

`OperitApplication.initializeMainApplicationLocked()` starts the coordinator after WorkManager
initialization. The startup work remains asynchronous; invocation readiness is the synchronization
point, so a run cannot reserve a lease while recovery is still in progress.

## 2. Model Resolution Boundary

`core/agent/model/AgentModelContract.kt` now exposes typed `AgentModelResolution` and
`AgentModelResolver` contracts. The provider implementation is
`OpenAiResponsesAgentModelResolver`.

Resolution input is only:

- model config ID
- model index

The OpenAI resolver:

- loads the selected `ModelConfigData`
- requires the exact config identity
- delegates strict provider, endpoint, option, and model validation to
  `OpenAiResponsesAgentSnapshot.fromModelConfig`
- returns encoded snapshot JSON and a request-scoped
  `OpenAiResponsesAgentModelClient`
- resolves the credential at request time through the existing credential provider

Unsupported providers are rejected by the strict snapshot validator. No provider state or API key
is placed in the persisted model snapshot.

## 3. Invocation Contract

`core/agent/runtime/AgentInvocationEntry.kt` exposes:

```text
AgentInvocationRequest
  chatId
  userText
  modelConfigId
  modelIndex
  promptSnapshot
  permissionSnapshotJson
  toolSnapshotJson
  userTimestamp
```

`execute()` returns a cold `Flow<AgentKernelEvent>`. On collection it:

1. awaits startup recovery
2. resolves the chat route
3. requires `AgentRoute.Plugin` and its root session
4. resolves the typed model client and model snapshot
5. creates `AgentKernelCommand` with the supplied immutable snapshots
6. collects the existing `AgentKernel` event stream

Legacy routes are rejected before model resolution. This entry does not derive prompts from the
Legacy `SystemPromptConfig`, character cards, ToolPkg registrations, or role cards. The host must
provide the prompt, permission, and tool snapshots explicitly until their Agent-specific resolvers
are implemented.

`OpenAiResponsesAgentInvocationEntryFactory` is the provider composition point. It wires the real
`AgentExecutionRepository`, `ModelConfigManager`, OpenAI resolver, and the Application-owned
startup coordinator. The generic runtime entry itself has no OpenAI dependency.

## 4. Tests Added

- `AgentRuntimeStartupCoordinatorTest`
  - one recovery pass
  - readiness result
  - duplicate start rejection
- `AgentInvocationEntryTest`
  - plugin route resolves the requested model and reaches Kernel completion
  - Legacy route is rejected before model resolution

The tests were executed in an isolated server worktree after explicit user permission:

```text
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1 \
  --tests "com.ai.assistance.operit.core.agent.*" \
  --tests "com.ai.assistance.operit.api.chat.llmprovider.agent.*" \
  --stacktrace
```

Result: `BUILD SUCCESSFUL in 19m 57s`; 33 tests executed, 33 passed, 0 failures, 0 errors.
The report included both new runtime tests, the Kernel suite, Responses adapter fixtures, the
adapter-to-Kernel integration test, and existing Agent contract/history/router tests.

The suite was rerun after the persistence and cancellation additions on functional commit
`fcaf21970`: `BUILD SUCCESSFUL in 29s`; 34 tests executed, 34 passed, 0 failures, 0 errors.
The new real-socket cancellation test also passed independently in `1m 38s`.

Remote `development` sync and `build_dev` passed for `d87481a78`:

- artifact: `operit-dev-development-d87481a7.apk`
- size: `403216031`
- SHA-256: `d47f960f79d46532d5a8858a6cfa5586c88a8bce8f88425d4be6b6ac5fd54500`

## 5. Remaining Gates

- Add a real in-flight SSE cancellation isolation fixture.
- Run the new migration and Repository instrumentation tests when an adb device is available.
- Add Room schema JSON baselines if the project enables Room schema export.
- Only after the gates pass, connect `AgentRouter` to an internal host entry. Legacy messages must
  continue through the existing LegacyPipeline.
