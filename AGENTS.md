# AGENTS.md

## Project Overview

OOPDI (`de.oopexpert.oopdi:oopdi-core`) is a lightweight, reflection-based dependency injection framework for Java 21. Managed beans are wrapped in Byte Buddy subclass proxies; the framework supports four scopes: GLOBAL, THREAD, LOCAL, REQUEST.

## Setup / Build / Test

```powershell
$env:JAVA_HOME = "C:\Daten\Programmierung\environments\jdk-21.0.5+11"
$env:PATH = "$env:JAVA_HOME\bin;C:\Daten\Programmierung\environments\apache-maven-3.9.15\bin;$env:PATH"
cd oopdi
mvn test
```

Test values (env vars, system properties) are injected by the Surefire plugin (see `pom.xml`). Do not rely on the developer machine having these set.

## Code Layout

```
oopdi/                                       ← Maven module root (pom.xml)
oopdi/src/main/java/de/oopexpert/oopdi/      ← framework source
oopdi/src/test/java/de/oopexpert/oopdi/      ← JUnit 5 tests
oopdi/src/test/java/de/oopexpert/teststructure/  ← fixture classes used by tests
Readme.md                                    ← project README
```

Core classes: `OOPDI` (entry point, owns the single `MetadataRepository` instance and, if enabled, `metadata.MetadataWarmup`), `Context` (orchestrates instance creation/injection/lifecycle via `InstanceFactory` and `LifecycleProcessor`; owns the `ShutdownStatus` state machine), `ProxyManager` (Byte Buddy proxies via `proxy.ByteBuddyProxyFactory`, REQUEST-scope `ThreadLocal` via `proxy.RequestScopeManager`), `metadata.MetadataRepository`/`ClassMetadata` (single source of truth for a class's primary constructor, field injection points, `@PostConstruct`/`@PreDestroy` methods), `metadata.MetadataMode` (`DISABLED`/`METADATA_ONLY`/`WARMUP_FAIL_FAST`/`WARMUP_LENIENT`, configured via `oopdi.metadata.mode` system property, default `DISABLED`), `metadata.MetadataWarmup`/`WarmupStatus` (background classpath scan that pre-populates the metadata cache when a `WARMUP_*` mode is active), `resolver.DependencyResolutionContext` (public: `getOrCreate` only) / `resolver.InternalResolutionContext` (framework-internal: additionally proxy issuance), `ScopedInstances` (maps `Scope → InstancesState`), `InstancesState` (per-scope instance cache + locks), `ClassesResolver` (delegates classpath scanning to `ClasspathScanner` and filtering to `InjectableFilter`), `Scope` (enum, polymorphic scope selection).

## Key Architectural Rules

- Every managed bean is a Byte Buddy subclass proxy; managed classes must not be `final`.
- Scope selection is polymorphic (`Scope` enum) — no switch statements.
- Per-class locks allow parallel creation of different beans while serializing creation of the same bean. The shared per-scope instance cache itself (`InstancesState.instances`) is a synchronized insertion-ordered map: different beans in the same scope may be written concurrently under different per-class locks, and `allInstances()`/`allInstancesInReverseCreationOrder()` return defensive snapshots under the map lock (never live views), so concurrent creation and shutdown iteration cannot corrupt the cache or throw `ConcurrentModificationException`.
- `immediate=true` is only valid for GLOBAL and THREAD scopes; it is invalid for LOCAL and REQUEST.
- GLOBAL scope singleton behavior is container-local — different `OOPDI` instances never share GLOBAL beans.
- GLOBAL/THREAD scoped proxies cache their resolved real object after the first method call (`ProxyManager.buildRealObjectSupplier`); LOCAL/REQUEST intentionally re-resolve on every call.
- Fixed: eligibility validation (`@Injectable` present, non-abstract) now runs before any proxy/real constructor executes (`ProxyManager.proxyIfNotExists(Class, Consumer, Function)` + `Context.validateEligible`), closing the premature-construction gap described in [.github/copilot-instructions.md](.github/copilot-instructions.md).
- Fixed: `ClassesResolver`'s classpath scan loads candidate classes without initializing them (`Class.forName(name, false, loader)`), so classes filtered out afterward (profile mismatch, abstract) never run their static initializers.
- Fixed: `Context.processField` only opens reflective access (`setAccessible(true)`) for fields that carry an inject annotation, not for every declared field.
- Fixed: the "exactly one constructor" invariant (`MultipleConstructors`) is now validated in exactly one place, `metadata.MetadataRepository`; `proxy.ByteBuddyProxyFactory` no longer duplicates that check itself and asks `MetadataRepository` for the primary constructor instead.
- Background metadata warmup (`metadata.MetadataMode.WARMUP_FAIL_FAST`/`WARMUP_LENIENT`, opt-in via `oopdi.metadata.mode`, default `DISABLED`): a daemon thread eagerly classpath-scans all `@Injectable` classes and pre-populates `MetadataRepository`'s cache. No retries ("fail is fail"). A single candidate class failing metadata inspection is logged and skipped, not job-fatal; only a failure of the scan itself sets `WarmupStatus.FAILED`. Under `WARMUP_FAIL_FAST` the next `OOPDI.getInstance()`/`getContext()` call after a `FAILED` scan throws `WarmupFailed`; under `WARMUP_LENIENT` it is only logged and the framework keeps working via the pre-existing on-demand (lazy) metadata resolution.
- Shutdown is state-managed (`ShutdownStatus`: `ACTIVE` → `SHUTTING_DOWN` → `SHUTDOWN`/`FAILED`, single-winner CAS, idempotent): a single guard at the top of `Context.getOrCreate` — the sole funnel for all real-object creation — throws `ContainerShutdown` for anything requested after shutdown began (top-level and nested alike). `shutdown()` destroys best-effort in drain-loop passes until no undestroyed instances remain (in-flight chains cannot be aborted and are picked up by later passes), aggregating `@PreDestroy` failures as suppressed exceptions. Failed post-processing (field injection/`@PostConstruct`) compensates via `InstancesState.remove`, so no half-initialized bean stays cached. Proxy issuance (`getOrCreateProxy`) is framework-internal only (`resolver.InternalResolutionContext`; removed from the public `DependencyResolutionContext`).

Full architecture, concurrency, and lifecycle details: see [.github/copilot-instructions.md](.github/copilot-instructions.md).

## Coding Conventions

- Exactly one constructor per managed class.
- Exactly one `@PostConstruct` and one `@PreDestroy` per class hierarchy (both traverse superclasses); `@PreDestroy` methods take no parameters.
- `@Injectable` is required on every class the framework instantiates.
- `@InjectSet` requires a `hint` (generic type erasure).
- `@InjectVariable defaultValue=""` means "no default".
- API boundary: the public contract is `OOPDI`, the annotations, `DependencyResolutionContext`, the exceptions, and the `MetadataMode`/`ShutdownStatus`/`WarmupStatus` enums. Casts to internal types (e.g. `InternalResolutionContext`) and reflective access to framework internals are unsupported territory — developers who bypass the public API this way keep their application consistent themselves. No JPMS enforcement is planned.

## Testing Instructions

- Framework: JUnit Jupiter 5.
- Test classes: `Test*` under `oopdi/src/test/java/de/oopexpert/oopdi/`, one feature per class (e.g. `TestScopeBehavior`, `TestLifecycleHooks`).
- Fixture classes: `Class*` under `oopdi/src/test/java/de/oopexpert/teststructure/`.
- Run all tests with `mvn test` (see Setup above) before considering any change complete.

## Release Process

Releases are versioned via a single manually triggered workflow (`.github/workflows/release-version.yml`, `workflow_dispatch`) that, in one run, commits the version bump directly to `main` (no pull request — repo rules block Actions-created PRs here), builds with Maven, publishes the jar to GitHub Packages (`de.oopexpert.oopdi:oopdi-core`), and creates the GitHub Release. Conventional Commits drive version bumps: `fix:`=patch, `feat:`=minor, `!:`/`BREAKING CHANGE:`=major, other prefixes=no release. Full details in [README.md](README.md#release-process) and [.github/copilot-instructions.md](.github/copilot-instructions.md).

## Commit / PR Guidelines

- One commit per task/change/feature.
- Complete the full implementation (code + tests) before committing.
- Run `mvn test` and confirm all tests pass before committing.
- Commit messages use the imperative mood and explain the *why*, not just the *what*.

## Self-Maintenance

When a change reveals a new verified repository-specific insight (fixed behavior, scope rule, lifecycle constraint, testing rule), update `.github/copilot-instructions.md` and repository memory (`/memories/repo/oopdi.md`) in the same task. Only update this file for structural, build, or workflow changes; keep it concise.
