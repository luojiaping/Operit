---
fork: https://github.com/luojiaping/Operit.git
scope: AgentRouter、chat active root session binding 和 owner-aware history reader
status: in_progress
---

# Agent Router And History Boundary

## 1. Confirmed Contract

- One chat may retain multiple root and child Agent sessions.
- One chat may explicitly bind one active root session at a time.
- Child sessions never become the chat route automatically.
- A chat without an explicit binding uses `LegacyRoute`.
- Routing never infers an Agent from role name, creation time, tool name, view ID, or global state.

## 2. Persistence

Room moves from version 22 to 23.

The new `agent_chat_bindings` table stores:

- `chatId`
- `activeSessionId`
- `updatedAt`

A composite foreign key requires the active session to belong to the same chat. The
repository additionally requires that the bound session is a root session with depth zero.
Moving that session to a terminal state clears its binding in the same transaction.

`agent_message_owners` now uses composite foreign keys to both messages and Agent sessions.
Plugin and Agent identity are read from the session rather than duplicated in the owner row.
This prevents a message, owner row, and session from referring to different chats. The
22-to-23 migration checks existing owner rows before rebuilding the constraints and rejects
inconsistent data instead of deleting it.

## 3. Stable Message Identity

`AgentExecutionRepository.persistAgentMessage()` inserts an Agent message and its owner row
inside one Room transaction and returns `PersistedAgentMessageRef` with the real Room
`messageId`. Update and owner-binding operations validate the same chat and session identity.

The existing `ChatMessage`, `MessageEntity` fields, timestamp-based public chat APIs, and
ToolPkg payloads remain unchanged.

Legacy chat archive and branch formats do not contain Agent identity. Exporting or replacing a
chat with Agent sessions, and branching a range containing Agent-owned messages, is rejected so
that owner records cannot be silently removed. Raw database backup and restore retain all Agent
tables. Chat metadata writes use Room upsert semantics and no longer delete child rows through
SQLite replace behavior.

## 4. Router

`AgentRouter` is a pure decision layer. It receives a chat ID, an explicit binding snapshot,
and the bound session snapshot, then returns either:

- `AgentRoute.Legacy`
- `AgentRoute.Plugin`

The router validates chat identity, session identity, and root-session status. No production
send path calls it yet because the AgentKernel model execution path does not exist.

## 5. History Reader

`AgentHistoryRepository` selects shared user messages and the requested session's owned messages
before reading their full contents and selected variants in a Room transaction.
`AgentHistoryAdapter` maps them into stable `AgentHistoryItem` values ordered by `orderIndex` and
`messageId`.

Ownership mapping is explicit:

- user -> shared user
- assistant or summary without an owner row -> legacy role card
- assistant or summary with an owner row -> that plugin Agent session

The existing pure projection then selects Legacy history or one plugin session. Tool-call
records remain in the execution tables and are not synthesized as chat messages.

## 6. Legacy Boundary

This stage does not modify `MessageCoordinationDelegate`, `AIMessageManager`,
`EnhancedAIService`, `ConversationService`, group orchestration, native summary, character
cards, Chat View Slot, or public ToolPkg types. Existing sends therefore continue through the
unchanged LegacyPipeline.

## 7. Verification

Added pure JVM test sources for:

- unbound Legacy routing
- explicit root-session routing
- child-session and cross-chat rejection
- stable message ordering
- shared, legacy, and plugin owner mapping
- invalid user ownership and unknown sender rejection

Device verification after the dev build must check Room 23, the new binding table, composite
foreign keys, retained v22 data, and unchanged Legacy chat behavior. Repository operations can
be exercised once a host test entry point or AgentKernel registration flow exists.

## 8. Next Stage

Implement `AgentKernel` and a typed Agent execution entry point. Only then should
`MessageCoordinationDelegate`, before group orchestration and role-card resolution, call the
router and dispatch `AgentRoute.Plugin` away from the LegacyPipeline.
