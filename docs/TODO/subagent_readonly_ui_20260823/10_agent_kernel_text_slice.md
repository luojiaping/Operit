---
fork: https://github.com/luojiaping/Operit.git
scope: typed Agent model protocol、单 active run 和 text-only AgentKernel
status: in_progress
---

# Text-only AgentKernel Slice

## 1. Goal

This stage implements an internal, testable Agent execution slice without connecting it to the
production chat sender. One explicitly bound root session can reserve one run, persist its user
input, consume a typed model event flow, commit one owned assistant message, and return to idle.

## 2. Contract Closure

Session, run, and step status are separate:

- `AgentSessionStatus`: multi-turn operational state
- `AgentRunStatus`: one execution and its terminal result
- `AgentStepStatus`: one model request

`AgentModeId` is an open opaque ID. `AgentProfileKind` separately identifies primary, subagent,
or all-profile declarations. Provider call references, model request IDs, step IDs, run IDs, and
host tool-call IDs are distinct types.

## 3. Room 24

Room version 24 adds:

- `agent_steps`
- `agent_run_leases`
- session `profileKind`
- run tool snapshot, input/output message IDs, and error code

The lease table has one row per session and a unique run ID. Run reservation creates the input
message, run, first step, lease, and running session state in one transaction. Completion writes
the assistant message and owner before completing the step/run, deleting the lease, and returning
the session to idle in the same transaction.

Reservation also materializes the owner-aware history inside that transaction and returns it as
an immutable run snapshot. The model request therefore cannot observe messages committed after
its input message.

## 4. Typed Model Protocol

`AgentModelClient` returns one ordered `Flow<AgentModelEvent>` containing:

- text delta
- reasoning delta
- complete tool call
- usage
- completed terminal event
- failed terminal event

Each execute call is one provider transport attempt. Event indexes must start at zero and be
contiguous, IDs must match the request, usage appears at most once, and exactly one terminal event
is required. Cancellation uses the coroutine job rather than a provider-global cancel method.

## 5. Kernel Behavior

The V1 kernel supports exactly one text-only step. It reads shared user messages and the target
session history, emits live typed kernel events, accumulates text/reasoning/usage, then invokes a
transactional terminal command. A tool call or tool-call stop reason ends the run with
`TOOLS_NOT_ENABLED` and executes no tool.

Cancellation settles the run and lease in `NonCancellable` context before propagating coroutine
cancellation. Repeated terminal commands are accepted only when their persisted payload matches.
Partial text, reasoning, and usage are retained on failed or cancelled steps. An explicit
`recoverInterruptedRuns()` command marks leases left by a prior process as failed and returns
their sessions to idle; the production startup coordinator must invoke it before routing is
enabled.

## 6. Tests Added

Pure JVM test sources cover:

- successful text/reasoning/usage completion
- typed provider failure
- tool-call rejection without side effects
- missing terminal event
- cancellation lease release
- partial payload retention on failure and cancellation
- interrupted-run recovery delegation
- session/run/step state transitions

Room migration and Repository integration still require device or instrumentation verification.

## 7. Production Boundary

This stage does not modify `MessageCoordinationDelegate`, `MessageProcessingDelegate`,
`AIMessageManager`, `EnhancedAIService`, providers, tools, permissions, ToolPkg APIs, or UI.
Legacy messages therefore continue through the existing pipeline and do not reserve Agent runs.

## 8. Next Stage

Implement a request-scoped OpenAI Responses typed adapter for text, reasoning, usage, errors, and
cancellation. Real routing remains disconnected until that adapter and kernel recovery behavior
pass their own verification gates.
