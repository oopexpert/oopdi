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

Core classes: `OOPDI` (entry point), `Context` (creates/injects/manages beans), `ProxyManager` (Byte Buddy proxies, REQUEST-scope `ThreadLocal`), `ScopedInstances` (maps `Scope → InstancesState`), `InstancesState` (per-scope instance cache + locks), `ClassesResolver` (classpath scan, profile filtering), `Scope` (enum, polymorphic scope selection).

## Key Architectural Rules

- Every managed bean is a Byte Buddy subclass proxy; managed classes must not be `final`.
- Scope selection is polymorphic (`Scope` enum) — no switch statements.
- Per-class locks allow parallel creation of different beans while serializing creation of the same bean.
- `immediate=true` is only valid for GLOBAL and THREAD scopes; it is invalid for LOCAL and REQUEST.
- GLOBAL scope singleton behavior is container-local — different `OOPDI` instances never share GLOBAL beans.
- GLOBAL/THREAD scoped proxies cache their resolved real object after the first method call (`ProxyManager.buildRealObjectSupplier`); LOCAL/REQUEST intentionally re-resolve on every call.
- Fixed: eligibility validation (`@Injectable` present, non-abstract) now runs before any proxy/real constructor executes (`ProxyManager.proxyIfNotExists(Class, Consumer, Function)` + `Context.validateEligible`), closing the premature-construction gap described in [.github/copilot-instructions.md](.github/copilot-instructions.md).
- Fixed: `ClassesResolver`'s classpath scan loads candidate classes without initializing them (`Class.forName(name, false, loader)`), so classes filtered out afterward (profile mismatch, abstract) never run their static initializers.
- Remaining hardening gap (planned): `Context.processField` opens reflective access before checking which inject annotation applies — see [.github/copilot-instructions.md](.github/copilot-instructions.md).

Full architecture, concurrency, and lifecycle details: see [.github/copilot-instructions.md](.github/copilot-instructions.md).

## Coding Conventions

- Exactly one constructor per managed class.
- Exactly one `@PostConstruct` and one `@PreDestroy` per class hierarchy (both traverse superclasses); `@PreDestroy` methods take no parameters.
- `@Injectable` is required on every class the framework instantiates.
- `@InjectSet` requires a `hint` (generic type erasure).
- `@InjectVariable defaultValue=""` means "no default".

## Testing Instructions

- Framework: JUnit Jupiter 5.
- Test classes: `Test*` under `oopdi/src/test/java/de/oopexpert/oopdi/`, one feature per class (e.g. `TestScopeBehavior`, `TestLifecycleHooks`).
- Fixture classes: `Class*` under `oopdi/src/test/java/de/oopexpert/teststructure/`.
- Run all tests with `mvn test` (see Setup above) before considering any change complete.

## Commit / PR Guidelines

- One commit per task/change/feature.
- Complete the full implementation (code + tests) before committing.
- Run `mvn test` and confirm all tests pass before committing.
- Commit messages use the imperative mood and explain the *why*, not just the *what*.

## Self-Maintenance

When a change reveals a new verified repository-specific insight (fixed behavior, scope rule, lifecycle constraint, testing rule), update `.github/copilot-instructions.md` and repository memory (`/memories/repo/oopdi.md`) in the same task. Only update this file for structural, build, or workflow changes; keep it concise.
