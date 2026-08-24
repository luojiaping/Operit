---
fork: https://github.com/luojiaping/Operit.git
scope: Room persistence, migration, Repository settlement, and SSE cancellation coverage
status: source_verified_pending_instrumentation
implementation_commit: 73b99d052
build_commit: 2ff742ae9
---

# Persistence Acceptance Coverage

This stage adds test seams and deterministic coverage for the gates that were previously only
covered by fake stores. Production Agent routing remains unchanged.

## 1. Repository Test Seam

`AgentHistoryRepository` and `AgentExecutionRepository` now accept an internal `AppDatabase`
dependency. Production `getInstance(context)` still uses the process database singleton; Android
tests can use an in-memory Room database with the same DAO and transaction code.

`AgentExecutionRepositoryAndroidTest` covers:

- completed Kernel run: shared user input, Agent assistant output, owner record, completed run and
  step, released lease
- provider failure: partial text, failure code, failed run and step, released lease
- cancellation: partial text, `CANCELLED` run and step, released lease
- process recovery: `PROCESS_INTERRUPTED`, failed run and step, idle session, deleted lease

## 2. Room 23→24 Migration

`AppDatabaseMigrationAndroidTest` uses the public `FrameworkSQLiteOpenHelperFactory` to create a
version-23 support database, then invokes the same `SupportSQLiteDatabase` migration path used by
Room. It verifies:

- `profileKind` and typed run columns
- interrupted status/error conversion
- `agent_steps` and `agent_run_leases` tables
- run, step, and lease indexes

The migration accessor is internal and test-only in visibility; production database construction
still registers the same migration instance.

## 3. SSE Cancellation

`OpenAiResponsesAgentModelClientTest.cancellingOneRequestClosesOnlyItsSseCall` uses a local
`ServerSocket`. The adapter still constructs the official OpenAI endpoint; the test interceptor
rewrites only the actual transport URL to localhost.

The test sends a partial first SSE response, cancels the Flow, verifies the server-side socket is
closed, then sends a second request through the same `OkHttpClient` and verifies a completed typed
response. This exercises real socket cancellation rather than an in-memory response body.

## 4. Verification

Targeted JVM command:

```text
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1 \
  --tests "com.ai.assistance.operit.core.agent.*" \
  --tests "com.ai.assistance.operit.api.chat.llmprovider.agent.*" \
  --stacktrace
```

Result on `fcaf21970`: 34 tests passed, 0 failures, 0 errors.

Android test source command:

```text
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=1 --stacktrace
```

Result on `73b99d052`: `BUILD SUCCESSFUL in 1m 16s`.

The generated Room 24 schema baseline is committed at
`app/schemas/com.ai.assistance.operit.data.db.AppDatabase/24.json`.

Remote `development` sync and `build_dev` passed for `2ff742ae9`:

- artifact: `operit-dev-development-2ff742ae.apk`
- size: `403216031`
- SHA-256: `ef8de9d44ef1f055fdc788dcdf7e2bc56f03a0f216b1d9a04e47c5ca97805b64`

No adb device was available on the build machine. The Android migration and Repository tests are
compiled but remain pending instrumentation execution.

## 5. Next Gate

Run when an adb device is available:

```text
./gradlew :app:connectedDebugAndroidTest --no-daemon --max-workers=1
```

with an available device. Only after those tests pass should an internal host entry be connected
to production routing. ToolPkg registration, permissions, UI, and Legacy sender changes remain
outside this stage.
