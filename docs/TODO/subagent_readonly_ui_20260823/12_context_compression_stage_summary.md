---
fork: https://github.com/luojiaping/Operit.git
scope: context compression recovery summary for Agent stages
status: active
current_commit: 30ec55b62
---

# Agent Workstream Stage Summary

This document is the recovery entry point after context compression. Read it before changing
Agent, Room, provider, ToolPkg, ChatView, or Legacy chat code. Stages 13-16 are implemented
through `30ec55b62`; the latest APK predates Stage 16.

## 1. Current Repository State

- Branch: `development`
- functional implementation commit: `6a6472bd1`
- latest test/documentation commit: `30ec55b62`
- build commit: `940c85554`
- Previous related commits:
  - `30d6c0124`: Chat View Slot baseline
  - `c295a4d28`: Room identity repair and Agent foundation persistence
  - `5ec4aa4a4`: explicit root-session routing and owner-aware history boundary
  - `bb46f8210`: text-only AgentKernel, Room 24, typed model contract
  - `3615e37c0`: official OpenAI Responses typed adapter and fixtures
- The local worktree has one pre-existing user change outside this workstream:
  `docs/TODO/chat_view_slot_plugin/index.md`. Do not revert or include it accidentally.
- Previous ToolPkg/ChatView APIs remain unchanged; Stage 16 adds a new draft Agent profile
  registration API.

## 2. Implemented Architecture

```text
LegacyPipeline
  ChatView -> MessageCoordinationDelegate -> AIMessageManager
  -> EnhancedAIService -> legacy AIService/XML/tool loop

Agent foundation
  Agent contract -> Room 24 -> AgentRouter -> owner-aware history

Agent kernel slice
  AgentKernel -> typed AgentModelClient -> AgentExecutionRepository
  -> Agent step/run/session/owner persistence

OpenAI adapter
  official Responses SSE -> typed AgentModelEvent -> AgentKernel
```

The OpenAI arrow is available through the host route bridge, but no ToolPkg profile can invoke an
Agent directly yet.

## 3. Room And Persistence

Current Room version is 24.

Tables include:

- `agent_sessions`
- `agent_runs`
- `agent_tool_calls`
- `agent_message_owners`
- `agent_chat_bindings`
- `agent_steps`
- `agent_run_leases`

Room 23 introduced explicit `chatId -> active root session` binding and composite owner
constraints. Room 24 introduced step records, run leases, profile kind, tool snapshot, input and
output message IDs, and error codes.

Important invariants:

- One chat can retain multiple root/child sessions.
- One chat has at most one explicit active root binding.
- Child sessions never become the chat route automatically.
- One session has at most one active run lease.
- Agent messages are persisted with real `messageId` and owner in one transaction.
- Agent history is selected by message ID before loading content.
- Legacy variants are rejected for Agent-owned messages.
- Legacy archive/branch operations reject Agent-owned sessions/messages rather than silently
  dropping owner records.

## 4. AgentKernel Slice

Key files:

- `core/agent/kernel/AgentKernel.kt`
- `core/agent/kernel/AgentKernelContract.kt`
- `core/agent/model/AgentModelContract.kt`
- `data/repository/AgentExecutionRepository.kt`
- `data/model/AgentStepEntity.kt`
- `data/model/AgentRunLeaseEntity.kt`

Kernel behavior:

- Exactly one text-only model step.
- Consumes ordered typed events with contiguous `eventIndex`.
- Supports text, reasoning, usage, completed, failed, and complete tool-call events.
- Tool calls end with `TOOLS_NOT_ENABLED`; no tool is executed.
- Cancellation settles run/step/lease in `NonCancellable` context.
- Partial text, reasoning, and usage are retained on failure/cancellation.
- `recoverInterruptedRuns()` marks stale leases as `PROCESS_INTERRUPTED` and returns sessions
  to `IDLE`.

`AgentRuntimeStartupCoordinator` now starts from
`OperitApplication.initializeMainApplicationLocked()` and blocks internal invocation until the
recovery pass completes. Production Router dispatch is still absent.

## 5. OpenAI Responses Adapter

Key files:

- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/agent/OpenAiResponsesAgentSnapshot.kt`
- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/agent/OpenAiResponsesAgentModelClient.kt`

Strict V1 boundary:

- Only `OPENAI_RESPONSES` with canonical provider ID.
- Only exact `https://api.openai.com/v1/responses` endpoint.
- No API key pool.
- No custom headers or custom parameters.
- Snapshot contains schema, credential reference, resolved single model, and optional
  `maxOutputTokens`; it never contains API key.
- Only `maxTokens` is supported as a standard model option. Other enabled options are rejected.
- Uses dedicated normal-TLS OkHttp client, no redirects, request-local `Call` and cancellation.
- Does not use `SharedHttpClient`, `UnsafeModelSsl`, `EnhancedAIService`, XML, or Legacy
  provider state.

Typed SSE coverage:

- output/refusal text deltas
- reasoning deltas and summary events
- `response.queued`
- complete function call with preserved provider `call_id`
- usage
- `response.completed`
- `response.incomplete` with `max_output_tokens` -> `LENGTH`
- filtered/other incomplete -> provider failure
- top-level `type=error`
- HTTP/network/protocol errors
- Chat Completions `[DONE]` is rejected

## 6. Verification Already Done

### Device

Installed APK:

- package: `com.ai.assistance.operit.dev`
- commit: `3615e37c0`
- size: `403216031`
- SHA-256: `6a85735168164b30404b894a148004f5a7f9953fdf40248154131e3cc1141855`

Device result:

- Room `user_version = 24`
- APK/database identity hash matched
- integrity, quick, and foreign-key checks passed
- Agent tables and adapter classes present
- Legacy send/reply/restart persistence passed
- Ordinary Legacy send created no Agent rows
- no production Router -> adapter call exists

### Remote Build

`build.md` remote sync and dev build passed for `3615e37c0`:

- artifact: `operit-dev-development-3615e37c.apk`
- size: `403216031`
- SHA-256: `6a85735168164b30404b894a148004f5a7f9953fdf40248154131e3cc1141855`

Stage 13 remote sync and dev build passed for `d87481a78`:

- artifact: `operit-dev-development-d87481a7.apk`
- size: `403216031`
- SHA-256: `d47f960f79d46532d5a8858a6cfa5586c88a8bce8f88425d4be6b6ac5fd54500`

### Server JVM Tests

Tests ran in an isolated server worktree with the builder source and submodule dependencies
linked into the temporary worktree. The original builder source and 8443 service were not
stopped or modified.

Targeted command:

```text
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1 \
  --tests "com.ai.assistance.operit.core.agent.*" \
  --tests "com.ai.assistance.operit.api.chat.llmprovider.agent.*" \
  --stacktrace
```

Result: `BUILD SUCCESSFUL`. Agent contract, Router, history, Kernel, Responses fixture, and
adapter-to-Kernel integration tests passed.

Full Debug JVM suite:

- 1214 tests executed
- 1212 passed
- 2 unrelated failures:
  - `XaiProviderReasoningTest.defaultConfigUsesTheOfficialXaiEndpointAndModel`: JVM test calls
    unmocked `android.util.Log.d`.
  - `ReleasedProviderModelKeyDecoderTest.pre provider model function key is skipped`: test
    expects `null`, decoder returns `FILE:BINDING`.

The temporary worktree was removed after testing. No Android device/`adb` was available on the
server, so instrumentation and real Room Repository tests were not run.

Stage 13 and 14 JVM tests were executed in an isolated server worktree after explicit user permission:

```text
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1 \
  --tests "com.ai.assistance.operit.core.agent.*" \
  --tests "com.ai.assistance.operit.api.chat.llmprovider.agent.*" \
  --stacktrace
```

Result for the initial Stage 13 run: `BUILD SUCCESSFUL in 19m 57s`; 33 tests executed, 33 passed,
0 failures, 0 errors.

After the Room/Repository seam and real-socket cancellation fixture were added, the targeted
suite was rerun on functional commit `fcaf21970`: `BUILD SUCCESSFUL in 29s`; 34 tests executed,
34 passed, 0 failures, 0 errors. The cancellation test also passed alone in `1m 38s`.

The Android test source compiled on commit `73b99d052` with
`:app:compileDebugAndroidTestKotlin`: `BUILD SUCCESSFUL in 1m 16s`. No adb device was available,
so the Android migration and Repository tests were not executed.

Remote `development` sync and `build_dev` passed for documentation head `2ff742ae9`, with the
functional code from `73b99d052`:

- artifact: `operit-dev-development-2ff742ae.apk`
- size: `403216031`
- SHA-256: `ef8de9d44ef1f055fdc788dcdf7e2bc56f03a0f216b1d9a04e47c5ca97805b64`

The generated Room 24 baseline is now committed at
`app/schemas/com.ai.assistance.operit.data.db.AppDatabase/24.json`.

The Stage 15 route bridge build passed after sync to `940c85554`:

- artifact: `operit-dev-development-940c8555.apk`
- size: `403232415`
- SHA-256: `072b0320df37431be3d072c85c315795f60e0c65a9580be30dd81906d377a4d4`

Route coordinator JVM coverage was added after the build and passed on test commit `4e70c2077`:
2 tests executed, 2 passed, 0 failures, 0 errors.

The lifecycle hardening build passed after sync to `37152bdc7`:

- artifact: `operit-dev-development-37152bdc.apk`
- size: `403236511`
- SHA-256: `7f0c33bdd337478cdaf3bcaa65c4ab7a79d37e9172702a582debd03785db0bb0`

The hardened targeted JVM suite contains 36 tests, all passed on `cfead4225`. Main app compilation
and Android test source compilation also passed.

Stage 16 registry/codec/coordinator tests and ToolPkg compatibility tests passed on `30ec55b62`;
the selected registry/capture suites were all green. No APK build has been triggered for Stage 16.

The temporary worktree was removed after the report was collected.

## 7. Remaining Gates

Before production Agent routing:

1. Run Android migration and Repository instrumentation when an adb device is available.
2. Add a real ToolPkg archive-to-runtime registration test.
3. Add profile-specific model/executor resolution and direct plugin invocation.
4. Only after those gates, add permission evaluation and typed tool execution.

Do not implement ToolPkg Agent registration, permissions, tool materialization, Plan/Build UI, or
production MessageCoordinationDelegate routing in the next small change unless the corresponding
gate is explicitly expanded.

## 8. Safe Resume Procedure

1. Read this file and documents `09`, `10`, and `11`.
2. Check `git status`; preserve the unrelated Chat View Slot document change.
3. Do not run local Gradle by default; the repository instructions require explicit permission.
4. For verification, use the remote builder or an isolated worktree, not `/srv/operit/source`.
5. Keep LegacyPipeline unchanged until the remaining gates pass.
