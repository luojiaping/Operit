---
fork: https://github.com/luojiaping/Operit.git
scope: official OpenAI Responses typed AgentModelClient
status: in_progress
---

# OpenAI Responses Typed Adapter

## 1. Goal

This stage provides the first real `AgentModelClient` implementation. It is request-scoped and
only supports the official OpenAI Responses endpoint. It remains disconnected from
`MessageCoordinationDelegate` and the production Agent route.

## 2. Snapshot And Credentials

`OpenAiResponsesAgentSnapshot` stores only:

- schema identifier
- Model configuration reference
- one resolved model ID
- optional resolved `maxOutputTokens`

It does not store API keys, key pools, endpoints, custom headers, custom parameters, or model
configuration JSON. The snapshot parser rejects unknown fields and unresolved multi-model IDs.
`maxTokens` is the only supported standard parameter in this text slice and is frozen as
`max_output_tokens`; other enabled standard parameters are rejected rather than ignored.

`ModelConfigOpenAiResponsesAgentCredentialProvider` resolves credentials at request time and
accepts only a single-key configuration with:

- `OPENAI_RESPONSES` enum and canonical type ID
- exact `https://api.openai.com/v1/responses` endpoint
- no API key pool
- no custom headers
- no custom parameters

The adapter uses the fixed official endpoint regardless of any caller-provided URL. It uses a
dedicated normal-TLS `OkHttpClient`, does not use `SharedHttpClient`, follows no redirects, and
does not log request headers or credential values.

## 3. Typed SSE Mapping

One `execute()` call creates one local `Call`, response body reader, SSE parser, function-call
accumulator, and cancellation scope. Coroutine cancellation closes only that call.

The adapter maps Responses events into the Agent contract:

- output/refusal delta -> `TextDelta`
- reasoning delta -> `ReasoningDelta`
- completed function call -> `ToolCallReady`
- completed usage -> `Usage`
- completed response -> `Completed`
- HTTP, provider, network, snapshot, or protocol errors -> one `Failed`

Function call IDs remain `ProviderToolCallRef`; they are not host `AgentToolCallId` values. Tool
arguments must be a completed JSON object before `ToolCallReady` is emitted.

## 4. Text-only Boundary

The V1 request requires an empty tool snapshot. If the server produces a function call, the
adapter emits the typed call and terminal tool-call result; the text-only Kernel then records
`TOOLS_NOT_ENABLED` without executing a tool.

No Chat Completions compatibility path, XML conversion, generic Responses endpoint, key rotation,
custom header, custom parameter, ToolPkg, permission, or production routing code is included.

## 5. Tests

Added JVM fixture tests for:

- strict, secret-free snapshot serialization
- official endpoint and Authorization header construction
- text, reasoning, usage, and completed ordering
- preserved provider function call IDs
- provider error mapping
- missing terminal protocol failure
- real typed adapter into the text-only Kernel with the JVM recording store

SSE fixtures live under the Agent adapter test resource path so protocol frames are reviewable
without decoding Kotlin string literals.

## 6. Implementation

The implementation is contained in:

- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/agent/OpenAiResponsesAgentSnapshot.kt`
- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/agent/OpenAiResponsesAgentModelClient.kt`
- `app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/agent/`
- `app/src/test/resources/com/ai/assistance/operit/api/chat/llmprovider/agent/fixtures/`

The adapter uses a standard SSE frame reader that accepts multi-line `data:` frames and stops at
the first typed terminal event. It rejects the Chat Completions `[DONE]` marker. OpenAI
`response.incomplete` maps to `LENGTH` only for `max_output_tokens`; filtered or otherwise
incomplete responses fail rather than committing partial output.

The adapter-to-Kernel JVM integration test uses the real typed adapter with the fixture HTTP
responder and the existing recording store. It verifies the text-only Kernel commits the typed
Responses output without entering the Legacy XML path.

## 7. Verification Status

- Static contract, SSE lifecycle, credential boundary, and change-scope review completed.
- `git diff --check` completed.
- Gradle build and JVM tests are pending the requested remote build or explicit test command.
- No production sender, Router, `EnhancedAIService`, ToolPkg, or public API integration exists.

## 8. Remaining Gates

Before production routing:

- execute the new JVM tests
- add real request cancellation fixture coverage
- add Adapter-to-Kernel integration coverage with the real Repository test seam
- add Room 23-to-24 migration tests and submit Room schema JSON baselines
- register `recoverInterruptedRuns()` in the startup coordinator

## 9. Next Stage

After the adapter and recovery gates pass, add an explicit host-side model snapshot resolver and
an internal Agent invocation entry. Production `AgentRouter` dispatch remains after that entry is
validated; Legacy messages continue through `EnhancedAIService` unchanged.
