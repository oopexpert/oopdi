# OOPDI — Repository Memory

Operational knowledge for future sessions. Verified facts only (code + tests).

## Build / Test

```powershell
$env:JAVA_HOME = "C:\Daten\Programmierung\environments\jdk-21.0.5+11"
$env:PATH = "$env:JAVA_HOME\bin;C:\Daten\Programmierung\environments\apache-maven-3.9.15\bin;$env:PATH"
cd oopdi
mvn test
```

- Java 21, no `--enable-preview` anywhere (stable APIs only — explicitly no JEP 430/431 templates).
- Test values (env vars, system properties) come from the Surefire plugin in `oopdi/pom.xml`
  (`environmentVariables`/`systemPropertyVariables`); Eclipse needs them configured manually
  (`-DdbUsername=dbUser1 -Dcounter=4`, env `dbUrl=jdbc://mysql:userdb`).
- PowerShell 5.1 in this environment: no `&&` chaining (use `;` / `if ($?)`), no `head`/`grep`
  (use `Select-String`), `Out-File`/`>` redirection encoding pitfalls (UTF-16 default for `>`,
  no `utf8NoBOM` in PS 5.1 `Set-Content`), no Python. Prefer dedicated file tools over shell
  for file ops; avoid `git apply` patch surgery — use exact-match edits instead.
- 78 tests green (JUnit Jupiter 5, `Test*` per feature + `teststructure` fixtures).

## Architecture (verified)

- Every managed bean is a Byte Buddy subclass proxy; managed classes must not be `final`,
  need exactly one constructor and `@Injectable`.
- Generated proxy *classes* are cached globally and shared across containers; per-proxy state
  travels in instance fields (`proxy.ProxiedBeanCarrier`/`ProxyTarget`, `oopdi$` members excluded
  from interception). Cache pins bean classes for JVM lifetime.
- Single funnel: all real-object creation goes through `Context.getOrCreate`
  (sole caller of `InstanceFactory.getOrCreateInjectable`, sole `put` site). One guard there
  throws `ContainerShutdown` after shutdown began. Proxy issuance is framework-internal
  (`InternalResolutionContext`); public surface is `getOrCreate` via `OOPDI.getInstance`.
- `metadata.MetadataRepository` is the single source of truth (primary constructor, field
  injection points, lifecycle methods). Mode via `oopdi.metadata.mode` (`DISABLED` default,
  `METADATA_ONLY`, `WARMUP_FAIL_FAST`, `WARMUP_LENIENT`); `OOPDI.getWarmupStatus()`.
- Shutdown state machine (`ShutdownStatus`, single-winner CAS, idempotent): best-effort
  drain-loop destruction, `DestructionFailed` aggregation; `InstancesState.remove`
  compensates failed post-processing; `OOPDI.shutdownRequested` covers shutdown-before-use.
- REQUEST beans die at chain end (same best-effort semantics); LOCAL has no lifecycle end
  by design (call-transient, no `@PreDestroy` support planned).
- `OOPDI.validate()` dry-validates the reachable bean graph (no instantiation, no side
  effects) via `GraphValidator`, reusing runtime checks (`InstanceFactory` eligibility,
  shared `VariableDependencyResolver.requireVariableValue`); all problems aggregated into
  one `CannotInject`.
  Scope state is container-local for every scope (REQUEST via per-container
  `RequestScopeManager`, never static).
- Per-class locks for same-bean creation; shared caches are synchronized maps with snapshot
  reads; `ClassesResolver` fills atomically (`computeIfAbsent`).
- `directConstructionPhase` is save/restore (plain `ThreadLocal`, `remove()` at outermost end).
- THREAD has no supplier-level `ThreadLocal` (canonical cache in `ScopedInstances`,
  dropped at shutdown via `clearThreadStates`).

## Conventions (must hold)

- One commit per task/change/feature; `mvn test` green before committing; imperative messages,
  why not just what. `fix:`=patch, `feat:`=minor, `!:`/`BREAKING CHANGE:`=major, `docs:`=no release.
- Release = single manual workflow (`.github/workflows/release-version.yml`,
  `workflow_dispatch`), commits version bump directly to `main` (no PR), publishes to GitHub
  Packages (`de.oopexpert.oopdi:oopdi-core`).
- Error messages via `String.formatted()`, never `+` (stable API, no string templates).
- Failure taxonomy: `CannotInject` (eligibility/config), `DestructionFailed` (aggregation),
  `MultipleConstructors`/`MultiplePostConstructMethods`/`MultiplePreDestroyMethods`
  (cardinalities), `ClasspathScanFailed` (infra), `WarmupFailed`, `ContainerShutdown`,
  `NoRequestScopeAvailable`, `UnderConstruction` (internal, surfaced as `CannotInject`).
- English-only user messages. No switch statements (polymorphic `Scope`).
- API boundary: public contract is `OOPDI`, annotations, `DependencyResolutionContext`,
  exceptions, `MetadataMode`/`ShutdownStatus`/`WarmupStatus`. Casts to internal types and
  reflective access to internals are unsupported — developers bypassing keep their app
  consistent themselves. No JPMS enforcement planned.
- Docs to maintain with architecture changes: `.github/copilot-instructions.md`, `AGENTS.md`,
  `README.md`, `DEVELOPER_GUIDE.md` (German user guide, 13 chapters), this file.

## Open follow-ups (not started)

- Maven Central publishing (needs account + GPG + workflow secrets; POM not Central-ready).
  Deferred: GitHub Packages preferred, and no publishing at all for now.
- 1.0 API freeze decision pending (includes whether/when to reintroduce the reverted
  `@Deprecated(forRemoval = true)` ceremony — dropped as premature while OOPDI has no
  external users).
