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

`getOrCreateInjectable` in `Context` uses `synchronized(scopedMap)` around the entire `instanceExists → createInstance → put → processFields → postConstruct` block. This prevents the GLOBAL check-then-act race. Java's reentrant `synchronized` means dependency resolution inside the same thread re-enters without blocking.

The `constructorInjection` set (cycle detection) lives inside `InstancesState` and is also guarded by the same `synchronized(scopedMap)` block. It is per-thread for single-threaded use cases; the outer lock prevents cross-thread false positives.

## Annotations

- `@Injectable(scope, immediate, profiles)` — marks a class as managed
- `@InjectInstance` — field injection of a single managed bean
- `@InjectSet(hint=X.class)` — field injection of `Set<X>` (all active concrete subclasses)
- `@InjectVariable(key, source)` — injects env var (`SYSTEM`) or system property (`PARAMETER`); throws `RuntimeException` with key name if missing
- `@PostConstruct` — single post-injection init method; parameters are injected like constructor params

## Known Constraints

- Managed classes must not be `final` (cglib requires subclassing)
- Exactly one constructor per managed class — multiple constructors throw `MultipleConstructors`
- Exactly one `@PostConstruct` per class — multiple throw `MultiplePostConstructMethods`
- `@InjectSet` requires a `hint` because Java erases generic type parameters at runtime
- `@Injectable` is required on every class the framework instantiates — unannotated classes throw at resolution time
- `ScopedInstances.threadInstanceMaps` (`Thread → InstancesState`) is never cleaned up — known memory leak in thread-pool environments

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
| `ClassGlobalRace` | GLOBAL | Tests that GLOBAL scope creates exactly one instance under concurrent access |
