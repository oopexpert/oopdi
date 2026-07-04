# OOPDI — Agent Instructions

## Build & Test

```powershell
$env:JAVA_HOME = "C:\Daten\Programmierung\environments\jdk-21.0.5+11"
$env:PATH = "$env:JAVA_HOME\bin;C:\Daten\Programmierung\environments\apache-maven-3.9.15\bin;$env:PATH"
mvn test
```

Test values are injected by the Surefire plugin (see `pom.xml` `environmentVariables` / `systemPropertyVariables`). Do not rely on the developer machine having these set.

## Project Layout

```
oopdi/          ← Maven module root (pom.xml here)
src/main/java/de/oopexpert/oopdi/   ← framework source
src/test/java/de/oopexpert/oopdi/   ← JUnit 5 tests
src/test/java/de/oopexpert/teststructure/  ← fixture classes used by tests
../Readme.md    ← project README (one level above the Maven module)
```

## Architecture

Every managed bean is wrapped in a **cglib subclass proxy** at registration time. The proxy intercepts all method calls, resolves the correct real instance for the bean's scope, and delegates. Callers always hold a proxy reference, never the real object directly.

Key classes:
- `OOPDI` — entry point; holds a single `Context` (lazy, synchronized)
- `Context` — creates, injects, and manages real objects
- `ProxyManager` — cglib proxy creation and registry; hosts the REQUEST-scope `ThreadLocal`
- `ScopedInstances` — maps `Scope → InstancesState`; THREAD scope keyed by `Thread` object
- `InstancesState` — stores instances and construction-cycle sentinel for one scope/thread slot
- `ClassesResolver` — classpath scan to find concrete `@Injectable` subclasses; profile filtering
- `Scope` (enum) — each variant selects its own `InstancesState` (polymorphic, no switch)

## Scope Semantics

| Scope    | Instance lifetime | `immediate` supported |
|----------|-------------------|-----------------------|
| GLOBAL   | One per container, shared across all threads | Yes |
| THREAD   | One per thread per container | No — would silently collapse to GLOBAL |
| LOCAL    | New real object per proxy method call | No |
| REQUEST  | One per outermost proxy call chain on a thread (ThreadLocal + call-depth counter) | No |

**LOCAL** — `Scope.LOCAL.select()` returns `new InstancesState()` on every call, so each proxy dispatch constructs a fresh real instance. Direct `this.method()` calls inside the bean bypass the proxy and are unaffected.

**REQUEST** — `ProxyManager` manages a `ThreadLocal<InstancesState>` with a call-depth counter. The scope is live from the first proxy call on the thread until depth returns to zero; then the `ThreadLocal` is cleared.

## Concurrency

`getOrCreateInjectable` in `Context` uses `synchronized(scopedMap.getLockFor(c))` — a per-class lock backed by a `ConcurrentHashMap` in `InstancesState`. This means:
- Threads creating **different** beans proceed in parallel
- Threads racing for the **same** bean serialize correctly
- Java's reentrant `synchronized` means dependency resolution on the same thread re-enters the lock without blocking (no deadlock)

The `constructorInjection` set (cycle detection) lives inside `InstancesState` and is guarded by the same per-class lock.

`ScopedInstances.threadInstanceMaps` uses `WeakHashMap` so entries are GC'd when threads die (previously a memory leak in thread-pool environments).

## Annotations

- `@Injectable(scope, immediate, profiles)` — marks a class as managed
- `@InjectInstance` — field injection of a single managed bean
- `@InjectSet(hint=X.class)` — field injection of `Set<X>` (all active concrete subclasses or implementations)
- `@InjectVariable(key, source, optional=false, defaultValue="")` — injects env var (`SYSTEM`) or system property (`PARAMETER`); missing key throws unless `optional=true` or `defaultValue` is set
- `@PostConstruct` — single post-injection init method; parameters are injected; traverses full superclass hierarchy
- `@PreDestroy` — single cleanup method called by `OOPDI.shutdown()`; no parameters; traverses full superclass hierarchy

## Lifecycle

`OOPDI.shutdown()` invokes `@PreDestroy` on all stored real instances (GLOBAL + all live THREAD states). LOCAL and REQUEST scopes have no persistent instances and are unaffected.

`@PostConstruct` and `@PreDestroy` both traverse the full superclass hierarchy. Exactly one method total across the hierarchy is enforced for each.

## Logging

Bean creation is logged at `DEBUG` level via SLF4J (`Context` logger). `slf4j-api` is a `provided` dependency — consumers must supply a backend. `slf4j-simple` is `test`-scoped for the test JVM.

- Managed classes must not be `final` (cglib requires subclassing)
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

## Git Workflow

One commit per task, change, or feature. When implementing multiple changes:
- Complete the full implementation (code + tests) for one change
- Run `mvn test` to confirm all tests pass
- Commit that change with a descriptive message
- Move to the next change

Commit messages use the imperative mood and explain the *why*, not just the *what*.
