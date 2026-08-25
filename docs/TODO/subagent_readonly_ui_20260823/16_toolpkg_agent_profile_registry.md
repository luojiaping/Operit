---
fork: https://github.com/luojiaping/Operit.git
scope: ToolPkg Agent profile capture, validation, registry, and host bridge
status: implemented_pending_tool_execution
implementation_commit: 6a6472bd1
test_commit: 30ec55b62
---

# ToolPkg Agent Profile Registry

This stage replaces the hard-coded activation identity with a registry-owned profile and adds the
first real ToolPkg registration path. It intentionally does not expose tools or permissions yet.

## 1. Registration API

ToolPkg now exposes:

```ts
ToolPkg.registerAgentProfile({
  agentId,
  displayName,
  profileVersion,
  profileKind,
  modeId,
  promptKey,
  promptSnapshot,
  capabilities,
  permissions,
  tools,
})
```

The global `registerToolPkgAgentProfile` bridge and declarations in `examples/types` are also
present. The package ID is assigned by the host as `pluginId`; a package cannot choose another
owner identity.

## 2. Validation Boundary

`AgentPluginRegistrationCodec` converts static JSON into a typed profile registration.

- `agent_runtime_v1` is required
- profile identity and prompt fields are required
- duplicate qualified profiles are rejected
- disabled profiles are not resolvable
- non-empty `permissions` and `tools` are rejected until their host bridges exist
- the profile declaration, prompt snapshot, and empty capability snapshots are stored in the
  registry registration

`ToolPkgAgentProfileBridge` synchronizes enabled ToolPkg container registrations into the global
`AgentPluginRegistry`. The built-in text Agent is registered through the same registry via
`BuiltinTextAgentPlugin`.

Activation now requires a registered profile. It no longer creates a session from arbitrary
hard-coded metadata, and invocation resolves prompt/permission/tool snapshots from the registered
profile.

## 3. Tests

Executed suites on the isolated builder worktree:

- `AgentPluginRegistrationCodecTest`: 3/3
- `AgentPluginRegistryTest`: 4/4
- `AgentChatTurnCoordinatorTest`: 3/3
- existing Agent/Kernel/Responses/ToolPkg compatibility suite: all 53 tests passed
- updated `JsToolPkgRegistrationTest`: 5/5
- `:app:compileDebugAndroidTestKotlin`: passed

No device instrumentation was run, by explicit decision.

## 4. Not Yet Implemented

- ToolPkg Agent invocation callback or direct invoke API
- profile-specific model resolver
- permission evaluation
- tool materialization and typed tool calls
- child sessions and subagent execution
- profile UI selection beyond the built-in host control
- real ToolPkg archive integration test proving a package registration reaches the runtime bridge

This stage establishes registration identity and capability gating. It is not yet a tool-capable
plugin MVP.
