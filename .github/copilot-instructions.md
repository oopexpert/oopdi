# OOPDI — Agent Instructions

## Build & Test

```powershell
$env:JAVA_HOME = "C:\Daten\Programmierung\environments\jdk-21.0.5+11"
$env:PATH = "$env:JAVA_HOME\bin;C:\Daten\Programmierung\environments\apache-maven-3.9.15\bin;$env:PATH"
cd oopdi
mvn test
```

Test values are injected by the Surefire plugin (see `pom.xml` `environmentVariables` / `systemPropertyVariables`). Do not rely on the developer machine having these set.

### Running tests in Eclipse
Eclipse's built-in JUnit launcher does not read Surefire's `environmentVariables`/`systemPropertyVariables` from `pom.xml`. To run tests successfully in Eclipse, configure the Run Configuration manually:
- VM arguments: `-DdbUsername=dbUser1 -Dcounter=4`
- Environment variable: `dbUrl=jdbc://mysql:userdb`

(`--add-opens` flags are no longer needed since migration to Byte Buddy.)

## Release Process

Releases are versioned via Conventional Commits (`fix:`=patch, `feat:`=minor, `!:`/`BREAKING CHANGE:` footer=major, `chore:`/`docs:`/`test:`/`ci:`/`refactor:`=no release), with no pull request involved (repo rules disallow Actions-created PRs and workflow-file pushes without extra token scopes, so a PR-based tool like release-please does not work here):

A single workflow, `.github/workflows/release-version.yml` (`workflow_dispatch`), does everything in one run: scans commits since the last `vX.Y.Z` tag, computes the next version, bumps `oopdi/pom.xml`/`oopdi/CHANGELOG.md`, commits + tags directly on `main` (no-op if no `fix:`/`feat:`/breaking commit found), builds the jar with Maven, publishes it to GitHub Packages (`de.oopexpert.oopdi:oopdi-core`, via `distributionManagement` in `oopdi/pom.xml` + `actions/setup-java`'s `server-id`/`server-username`/`server-password` inputs), and creates the GitHub Release with the jar attached. There is no longer a separate tag-triggered workflow.

## Copilot Self-Maintenance

When a change introduces a verified repository-specific insight (for example: fixed behavior, scope rule clarification, lifecycle constraint, or test reliability rule), update GitHub Copilot helper docs automatically in the same task.

Required behavior:
- Update this file (`.github/copilot-instructions.md`) when the insight changes implementation guidance, architecture understanding, or testing guidance.
- Update `README.md` when the change affects the proxy model, scopes, lifecycle, annotation API, or any user-facing architectural description.
- Update `AGENTS.md` when the change affects the architecture overview, core class descriptions, or coding conventions.
- Update repository memory notes (`/memories/repo/oopdi.md`) when the insight is useful for future sessions and command execution.
- Only record verified facts (code + tests), never speculation.
- Keep updates concise and additive; prefer small targeted edits.
- If no new insight was discovered, do not edit helper files.

Pre-commit checklist for architecture-relevant changes (proxy library, scopes, lifecycle, annotations):
1. `.github/copilot-instructions.md` — updated?
2. `README.md` — updated?
3. `AGENTS.md` — updated?
4. `/memories/repo/oopdi.md` — updated?

Recent verified insights that must stay reflected in helper docs:
- `immediate=true` is invalid for THREAD, LOCAL, and REQUEST scopes.
- Misconfiguration for these scope/immediate combinations is observable as a runtime failure when the proxy-invoked method is executed.
- GLOBAL scope singleton behavior is container-local; different `OOPDI` instances do not share GLOBAL bean instances. Since the REQUEST-scope fix, this holds for every scope: REQUEST state is per-container (`RequestScopeManager` instance, never static), THREAD via per-container maps, LOCAL trivially.
- `ProxyManager.buildRealObjectSupplier` now lazily caches the resolved real object for GLOBAL (via a synchronized `AtomicReference`) and THREAD (via `ThreadLocal`) scopes, so a proxy method call after the first no longer re-runs `Context.getOrCreate`'s annotation checks and per-class lock acquisition. LOCAL and REQUEST scopes are intentionally excluded and still re-resolve on every call (LOCAL needs a fresh instance per call; REQUEST is thread/call-depth scoped).
- Confirmed (fixed): building a Byte Buddy proxy (`ProxyManager.proxy()`/`createProxyWith*Constructor`) previously invoked the real class's constructor immediately with dummy/null arguments *before* `Context`'s `@Injectable`/abstract validation ran. Fixed via `ProxyManager.proxyIfNotExists(Class, Consumer<Class<T>> eligibilityCheck, Function)`: the eligibility check now runs synchronously before `proxy()`/any constructor invocation. `Context.validateEligible` (checkInjectableAnnotated + checkNonAbstract) is passed as this check from every `proxyIfNotExists` call site (root class, field injection, `@InjectSet`, `getOrCreateInstance`). `checkImmediateInstantiationConfiguration` intentionally remains lazy (config-consistency check, not an eligibility gate). Note: constructor-injected parameters resolve via `Context.getOrCreate` directly (bypassing the proxy layer entirely, pre-existing design), so `getOrCreate` still performs its own `checkInjectableAnnotated`/`checkNonAbstract` for that path. Tests: `TestSecurityValidation.testNonInjectableClassConstructorNotInvokedBeforeValidation`, `testAbstractClassConstructorNotInvokedBeforeValidation`.
- Remaining known gap (fix planned, Phase C of session plan): `Context.processField` calls `field.setAccessible(true)` unconditionally before checking which inject annotation is present.
- Fixed: `ClassesResolver` classpath scan (`findAssignableClassInJarEntry`/`findAssignableClassInFile`) now loads candidate classes via `Class.forName(name, false, classLoader)` (non-initializing) instead of the eager-initializing 1-arg form. Classes that are scanned but end up filtered out (profile mismatch, abstract) no longer have their static initializers run as a side effect of the scan; a class is only initialized by the JVM later, when it is actually instantiated. Test: `TestSecurityValidation.testInjectSetClasspathScanDoesNotInitializeProfileFilteredCandidate` (uses a separate `ClassSetSideEffectTracker` class to observe the side effect without itself referencing/initializing the candidate class).
- Fixed: `Context.processField` no longer calls `field.setAccessible(true)` unconditionally for every declared field; the call now happens only inside the branch matching the annotation actually present (`@InjectInstance`/`@InjectSet`/`@InjectVariable`), narrowing reflective access to fields the framework actually injects. Note: `Field#setAccessible`'s override flag is per-`Field`-instance and not observable via a freshly obtained `Field` object, so this fix has no black-box regression test beyond the functional injection tests already in place (`TestSecurityValidation.testFieldProcessingStillInjectsAnnotatedFieldsCorrectly`).
- `metadata.MetadataRepository` (single source of truth for reflective class metadata; caching behavior controlled by `metadata.MetadataMode`, see below) is the **single source of truth** for a class's primary constructor, field injection points, and `@PostConstruct`/`@PreDestroy` methods (`ClassMetadata`). Every consumer that needs "which constructor to use" — real object instantiation (`InstanceFactory.getConstructor`) and Byte Buddy proxy stub generation (`proxy.ByteBuddyProxyFactory.createProxy`) — goes through `MetadataRepository.getMetadata(Class)` instead of re-deriving that information itself.
- Fixed: the "exactly one constructor" invariant (`MultipleConstructors`) was silently dropped from `InstanceFactory` during the metadata-caching refactor, leaving `ByteBuddyProxyFactory`'s own (inconsistent, `CannotInject`-throwing) `getDeclaredConstructors()` check as the only enforcement. Restored: `MetadataRepository.determinePrimaryConstructor` now throws `MultipleConstructors` when a class declares more than one constructor; `ByteBuddyProxyFactory` no longer re-derives or re-validates the constructor itself, it asks `MetadataRepository` for the primary constructor. `MetadataRepository` is created once in `OOPDI`'s constructor and passed down to both `ProxyManager` and `Context` (previously instantiated separately, lazily, inside `Context`). Test: `TestSecurityValidation.testMultipleConstructorsClassConstructorNotInvokedBeforeValidation` (fixture `ClassMultipleConstructorsWithSideEffect`, two constructors, verifies neither runs before `MultipleConstructors` is thrown).
- `metadata.MetadataMode` (enum: `DISABLED`/`METADATA_ONLY`/`WARMUP_FAIL_FAST`/`WARMUP_LENIENT`) replaced the earlier boolean `oopdi.cache.metadata` system property with a single `oopdi.metadata.mode` property (breaking rename — the old property is no longer read at all). Absent property → `DISABLED` (no cache, matches pre-existing default); an unrecognized value throws immediately in `MetadataMode.fromSystemProperty()` (fail-fast on typos, no silent fallback). `isCacheEnabled()`/`isWarmupEnabled()`/`isFailFast()` are derived, no external switch statements needed.
- Background metadata warmup (`metadata.MetadataWarmup`, active for `WARMUP_FAIL_FAST`/`WARMUP_LENIENT`): a daemon thread (`OOPDI`'s constructor starts it when `MetadataMode.isWarmupEnabled()`) runs `ClasspathScanner.findAllAnnotatedClasses(Injectable.class)` — a classpath-wide scan independent of any hint class/package, unlike the existing hint-scoped `findDerivedClasses` — filters via `InjectableFilter`, then calls `MetadataRepository.getMetadata(...)` per candidate to pre-populate the cache. No retries ("fail is fail"): a single candidate class failing metadata inspection (e.g. `MultipleConstructors`) is logged and skipped, not job-fatal; only a failure of the scan itself sets `WarmupStatus.FAILED` (tracked via `AtomicReference`, exposed as `OOPDI.getWarmupStatus()`, always `NOT_STARTED` when warmup isn't active). Under `WARMUP_FAIL_FAST` the next `OOPDI.getInstance()`/`getContext()` call after `FAILED` throws `WarmupFailed`; under `WARMUP_LENIENT` it is only logged and the framework keeps working via the pre-existing on-demand (lazy) metadata resolution — this is why `MetadataRepository`'s `computeIfAbsent`-based cache already made the warmup safe to add without touching the hot request path: cache misses (warmup not yet reached that class, or warmup disabled) transparently fall back to synchronous inspection. Fixed during implementation: the classpath-wide scan must defensively skip/catch `LinkageError` per candidate class (not just `ClassNotFoundException`) — `module-info.class` entries in JARs throw `NoClassDefFoundError` (a `LinkageError`, not caught by `catch (RuntimeException e)`) when loaded via `Class.forName`, which silently killed the background thread and hung `WarmupStatus` at `RUNNING` forever before the fix. Tests: `metadata.TestMetadataMode`, `metadata.TestMetadataWarmup`, `TestBackgroundWarmup`.
- Shutdown is state-managed, mirroring the warmup pattern (`ShutdownStatus`: `ACTIVE` → `SHUTTING_DOWN` → `SHUTDOWN`/`FAILED`, owned by `Context`, transitions via single-winner CAS so concurrent/repeated `shutdown()` calls are no-ops returning the terminal status; observed via `OOPDI.getShutdownStatus()`). A single guard at the top of `Context.getOrCreate` — verified as the sole funnel for all real-object creation (only caller of `InstanceFactory.getOrCreateInjectable`, only `put` site downstream; top-level lazy, `immediate` eager, and all nested constructor/field/set/lifecycle resolutions flow through it) — throws `ContainerShutdown` for anything requested after shutdown began. `shutdown()` destroys best-effort in drain-loop passes until no undestroyed instances remain (in-flight chains cannot be aborted — Java has no safe thread abortion — and are picked up by later passes; the loop terminates because the guard rejects every new request), aggregating `@PreDestroy` failures as suppressed exceptions on the thrown error. Failed post-processing (field injection/`@PostConstruct`) compensates via the new `InstancesState.remove`, so no half-initialized bean stays cached; the early `put` before post-processing is intentionally kept so field-injection cycles (A↔B via early exposure) keep working. Proxy issuance (`getOrCreateProxy`) was removed from the public `DependencyResolutionContext` interface into the framework-internal `InternalResolutionContext` (implemented only by `Context`; the two resolver call sites cast once with a comment) — external consumers program against `getOrCreate` only. Note: proxies handed out via `OOPDI.getInstance` after shutdown began are inert and throw `ContainerShutdown` on first method call via the same guard; already-resolved beans stay readable from the scope cache. Also fixed alongside: `MetadataWarmup.start()` is now CAS-guarded (`NOT_STARTED` → `RUNNING`) so a double start fails fast instead of launching a duplicate scan. Tests: `TestShutdownLifecycle` (fixtures `ClassFailingPreDestroy`, `ClassFailingPostConstruct`).
- REQUEST-scoped beans are destroyed at request-chain end (previously their `@PreDestroy` silently never ran — `invokePreDestroy` was only called from `Context.shutdown()`): `proxy.RequestScopeManager.intercept` captures the main-call outcome, then in the `finally` at `callDepth == 0` copies snapshots, clears the `ThreadLocal`, and destroys every request bean of that chain in reverse creation order, best-effort with the same aggregation semantics as shutdown (main-call failure propagates with cleanup failures suppressed; clean main call with cleanup failures throws aggregated). Wired via a `Consumer<Object>` destroyer set by `Context` after its `LifecycleProcessor` exists (same pattern as `InstanceFactory`'s post-processor; default `null` keeps old behavior in isolation). Nested chains share the outermost state and are destroyed once at its end; cleanup starting new chains gets a fresh context. LOCAL stays unaffected (call-transient, never cached). Tests: `TestRequestDestruction` (fixtures `ClassRequestPreDestroy`, `ClassRequestFailingPreDestroy`).
- Generated proxy *classes* are shared across containers: `proxy.ByteBuddyProxyFactory` keeps a static `ConcurrentHashMap` cache keyed by bean class. Sharing required handler indirection — the class no longer bakes the container-specific `realObjectSupplier`/`RequestScopeManager` into the `InvocationHandlerAdapter` closure; instead each proxy *instance* carries a `ProxyTarget` in `oopdi$`-prefixed fields, read through `ProxiedBeanCarrier` (both framework-internal, public only because generated classes load into the bean's class loader). The intercept matcher excludes carrier methods by declaration (previously plain `any()`). Instantiation still mimics the `MetadataRepository`-determined primary constructor with dummy defaults, then initializes the carrier. Known limitation: the static cache pins bean classes for the JVM lifetime (documented for hot-redeploy environments). Tests: `TestProxyBehavior.testProxyClassSharedAcrossContainers` (fails on old code with two distinct `ByteBuddy$…` classes), `testSharedProxyClassStillResolvesThroughOwningContainer`.
- `ProxyManager.nonProxyClazz` is private (single caller inside the proxies lock; no test usage) — no unsynchronized reads of the proxy registry.
- Scope selection *and* supplier creation are both polymorphic on the `Scope` enum (`select` + `createSupplier`); the `switch (Scope…)` formerly in `proxy.ScopedSupplierFactory` is gone (`ScopedSupplierFactory` remains as a delegating facade keeping its public constructor). This finally matches the long-documented "no switch statements" rule.

Copilot must continuously validate its own reasoning against verified project facts.  
Verified facts are exclusively those derived from:
- the actual code
- the test suite
- the architectural rules defined in this file
- existing repository memory entries

When Copilot detects that a previous assumption, explanation, or implementation
conflicts with verified facts, it must:

- correct the reasoning immediately
- update the current answer accordingly
- propose minimal, targeted code or test changes (never broad rewrites)
- update helper docs only when the corrected insight changes implementation guidance,
  architecture understanding, or testing rules

Self-correction must never rely on speculation.  
Tests and code are the single source of truth.


## Project Layout

```
oopdi/          ← Maven module root (pom.xml here)
src/main/java/de/oopexpert/oopdi/   ← framework source
src/test/java/de/oopexpert/oopdi/   ← JUnit 5 tests
src/test/java/de/oopexpert/teststructure/  ← fixture classes used by tests
../README.md    ← project README (one level above the Maven module)
```

## Architecture

Every managed bean is wrapped in a **Byte Buddy subclass proxy** at registration time. The proxy intercepts all method calls, resolves the correct real instance for the bean's scope, and delegates. Callers always hold a proxy reference, never the real object directly.

Key classes:
- `OOPDI` — entry point; holds a single `Context` (lazy, synchronized); owns the single `MetadataRepository` instance, created eagerly and passed to both `ProxyManager` and `Context`; starts `metadata.MetadataWarmup` when `MetadataMode.isWarmupEnabled()`; exposes `getWarmupStatus()`/`getShutdownStatus()`
- `Context` — orchestrates instance creation/injection/lifecycle; delegates to `InstanceFactory`, `LifecycleProcessor`, and a `resolver.DependencyResolverPipeline`
- `InstanceFactory` — validates eligibility (`@Injectable`, non-abstract), constructs real objects via reflection, resolves constructor parameters
- `LifecycleProcessor` — invokes `@PostConstruct`/`@PreDestroy`, using `MetadataRepository` to look up lifecycle methods
- `metadata.MetadataRepository` / `metadata.ClassMetadata` — single source of truth for a class's primary constructor, field injection points, and lifecycle methods (see insight above); caching controlled by `metadata.MetadataMode` (`oopdi.metadata.mode` system property, default `DISABLED`)
- `metadata.MetadataMode` — `DISABLED`/`METADATA_ONLY`/`WARMUP_FAIL_FAST`/`WARMUP_LENIENT`; `metadata.MetadataWarmup`/`WarmupStatus` — background classpath scan pre-populating the cache for `WARMUP_*` modes (see insight above)
- `resolver` package (`DependencyResolverPipeline`, `InjectionPoint`/`FieldInjectionPoint`/`ParameterInjectionPoint`, `impl.{Variable,Set,Instance}DependencyResolver`) — pluggable field/parameter injection strategies
- `ProxyManager` — Byte Buddy proxy creation and registry; delegates actual proxy-class generation to `proxy.ByteBuddyProxyFactory` and scoped-supplier creation to `proxy.ScopedSupplierFactory`; the per-container `proxy.RequestScopeManager` (one instance per container, never static) owns the REQUEST-scope `ThreadLocal`
- `ScopedInstances` — maps `Scope → InstancesState`; THREAD scope keyed by `Thread` object; also carries the container's `RequestScopeManager` for `Scope.REQUEST.select`
- `InstancesState` — stores instances and construction-cycle sentinel for one scope/thread slot
- `ClassesResolver` — determines the relevant concrete `@Injectable` class for a given type; delegates classpath scanning to `ClasspathScanner` and profile/injectable filtering to `InjectableFilter`
- `Scope` (enum) — each variant selects its own `InstancesState` (polymorphic, no switch)

## Scope Semantics

| Scope    | Instance lifetime | `immediate` supported |
|----------|-------------------|-----------------------|
| GLOBAL   | One per container, shared across all threads | Yes |
| THREAD   | One per thread per container | No — would silently collapse to GLOBAL |
| LOCAL    | New real object per proxy method call | No |
| REQUEST  | One per outermost proxy call chain on a thread (ThreadLocal + call-depth counter) | No |

**LOCAL** — `Scope.LOCAL.select()` returns `new InstancesState()` on every call, so each proxy dispatch constructs a fresh real instance. Direct `this.method()` calls inside the bean bypass the proxy and are unaffected.

**REQUEST** — the container's `RequestScopeManager` manages a `ThreadLocal<InstancesState>` with a call-depth counter. The scope is live from the first proxy call on the thread until depth returns to zero; then the `ThreadLocal` is cleared. The manager is per-container (wired once in `OOPDI`, shared by interception and selection), so nested call chains of different containers on the same thread stay isolated. Test: `TestScopeBehavior.testRequestScopeIsolatedAcrossDifferentContainers`.

## Concurrency

`getOrCreateInjectable` in `Context` uses `synchronized(scopedMap.getLockFor(c))` — a per-class lock backed by a `ConcurrentHashMap` in `InstancesState`. This means:
- Threads creating **different** beans proceed in parallel
- Threads racing for the **same** bean serialize correctly
- Java's reentrant `synchronized` means dependency resolution on the same thread re-enters the lock without blocking (no deadlock)

The shared per-scope instance cache (`InstancesState.instances`) is a `Collections.synchronizedMap`-wrapped `LinkedHashMap`: per-class locks only serialize same-bean creation, so different beans in the same scope are written concurrently under different locks. `allInstances()`/`allInstancesInReverseCreationOrder()` take defensive snapshots under the map lock (never live views), preserving insertion order for reverse-creation-order destruction while tolerating concurrent creation and shutdown iteration. Tests: `TestInstancesState.testConcurrentPutsForDistinctKeysStayConsistent`, `TestRequestAndConcurrency.testConcurrentCreationOfDifferentGlobalBeansStaysConsistent`.

The `constructorInjection` set (cycle detection) lives inside `InstancesState` and is guarded by the same per-class lock.

`ScopedInstances.threadInstanceMaps` uses `WeakHashMap` so entries are GC'd when threads die (previously a memory leak in thread-pool environments).

`ClassesResolver.determineRelevantClass` caches its result per input class in a `ConcurrentHashMap` (`relevantClassCache`), avoiding a full classpath re-scan on every bean resolution. The cache is scoped to the `ClassesResolver` instance (one per `OOPDI` container), so different containers/profiles never share cached results.

**Self-invocation bypasses the proxy**: calling `this.someMethod()` from inside a managed bean invokes the real object directly, not the Byte Buddy proxy — standard Java proxy behavior (same caveat in Spring/CDI). LOCAL's "fresh instance per call" and REQUEST's call-depth tracking do not apply to such calls. Documented in [README.md](../README.md).

## Annotations

- `@Injectable(scope, immediate, profiles)` — marks a class as managed
- `@InjectInstance` — field injection of a single managed bean
- `@InjectSet(hint=X.class)` — field injection of `Set<X>` (all active concrete subclasses or implementations)
- `@InjectVariable(key, source, optional=false, defaultValue="")` — injects env var (`SYSTEM`) or system property (`PARAMETER`); missing key throws unless `optional=true` or `defaultValue` is set. Supported field types for automatic parsing: `Integer`, `Long`, `Short`, `Float`, `Double`, `Boolean`, `Byte`, `Character` (primitive and boxed forms); anything else is assigned as `String`.
- `@PostConstruct` — single post-injection init method; parameters are injected; traverses full superclass hierarchy
- `@PreDestroy` — single cleanup method called by `OOPDI.shutdown()`; no parameters; traverses full superclass hierarchy

## Lifecycle

`OOPDI.shutdown()` invokes `@PreDestroy` on all stored real instances (GLOBAL + all live THREAD states), in **reverse creation order** per scope (`InstancesState` stores instances in a `LinkedHashMap`; `allInstancesInReverseCreationOrder()` reverses insertion order). This ensures a dependent bean is destroyed before the dependency it was built on. LOCAL and REQUEST scopes have no persistent instances and are unaffected.

`@PostConstruct` and `@PreDestroy` both traverse the full superclass hierarchy. Exactly one method total across the hierarchy is enforced for each.

## Logging

Bean creation is logged at `DEBUG` level via SLF4J (`Context` logger). `slf4j-api` is a `provided` dependency — consumers must supply a backend. `slf4j-simple` is `test`-scoped for the test JVM.

- Managed classes must not be `final` (Byte Buddy requires subclassing)
- Exactly one constructor per managed class — multiple constructors throw `MultipleConstructors`
- Exactly one `@PostConstruct` per class hierarchy — multiple throw `MultiplePostConstructMethods`
- Exactly one `@PreDestroy` per class hierarchy — multiple throw `RuntimeException`
- `@PreDestroy` methods take no parameters
- `@InjectSet` requires a `hint` because Java erases generic type parameters at runtime
- `@Injectable` is required on every class the framework instantiates — unannotated classes throw at resolution time
- `@InjectVariable defaultValue=""` means "no default" — an empty string cannot itself be a default value

## Test Fixtures

Test classes live under `de.oopexpert.teststructure`:

| Class | Scope | Purpose |
|-------|-------|---------|
| `ClassA` | GLOBAL | Variable injection (`@InjectVariable`) |
| `ClassB` (abstract) | — | Base for set injection tests; has field-injected `ClassC` |
| `ClassB1` | LOCAL | Concrete subclass; active with no profile |
| `ClassB2` | GLOBAL | Concrete subclass; active only with `profile1` |
| `ClassB3` | — | Not `@Injectable`; always filtered out |
| `ClassC` | THREAD | Thread-scope test target |
| `ClassD` | REQUEST | Request-scope test target |
| `ClassRoot` | LOCAL | Root bean; injects `ClassA`, `ClassD`, `Set<ClassB>` |
| `ClassMissingVar` | GLOBAL | Tests that missing `@InjectVariable` key throws descriptively |
| `ClassOptionalVar` | GLOBAL | Tests `@InjectVariable` `optional=true` and `defaultValue` |
| `ClassGlobalRace` | GLOBAL | Tests that GLOBAL scope creates exactly one instance under concurrent access |
| `ClassPostConstructBase` (abstract) | GLOBAL | Tests `@PostConstruct` superclass traversal |
| `ClassPostConstructChild` | GLOBAL | Concrete subclass of ClassPostConstructBase |
| `ClassWithPreDestroy` | GLOBAL | Tests `@PreDestroy` invocation on `shutdown()` |
| `ClassPreDestroyBase` (abstract) | GLOBAL | Tests `@PreDestroy` superclass traversal |
| `ClassPreDestroyChild` | GLOBAL | Concrete subclass of ClassPreDestroyBase |
| `ClassNotInjectableWithSideEffect` | — | Not `@Injectable`; tracks constructor calls to verify eligibility validation runs before construction |
| `ClassAbstractInjectableWithSideEffect` | — | Abstract; tracks constructor calls to verify abstractness validation runs before construction |
| `ClassSetSideEffectRoot` / `ClassSetSideEffectTracker` | GLOBAL | Verifies `@InjectSet` classpath scan does not initialize profile-filtered candidates |
| `ClassFieldAccessibilityTarget` | GLOBAL | Regression guard for narrowed `field.setAccessible(true)` scope |
| `ClassMultipleConstructorsWithSideEffect` | — | Declares two constructors; tracks constructor calls to verify `MetadataRepository` throws `MultipleConstructors` before either constructor runs |
| `ClassFailingPostConstruct` | — | `@PostConstruct` always throws; tracks constructor calls to verify failed post-processing leaves nothing behind in the cache |
| `ClassFailingPreDestroy` | GLOBAL | `@PreDestroy` always throws; tracks calls to verify best-effort shutdown continuation and aggregation |
| `ClassRequestPreDestroy` | REQUEST | Tracks `@PreDestroy` calls/identities to verify request-end destruction (exactly once, outermost chain end) |
| `ClassRequestFailingPreDestroy` | REQUEST | `@PreDestroy` always throws; tracks calls to verify best-effort request-end destruction semantics |

## Git Workflow

One commit per task, change, or feature. When implementing multiple changes:
- Complete the full implementation (code + tests) for one change
- Run `mvn test` to confirm all tests pass
- Commit that change with a descriptive message
- Move to the next change

Commit messages use the imperative mood and explain the *why*, not just the *what*.
