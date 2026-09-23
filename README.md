# OOPDI: Lightweight Dependency Injection Framework

1. [Introduction](#introduction)
   - Overview
   - Features

2. [Annotations](#annotations)
   - @Injectable
   - @InjectInstance
   - @InjectSet
   - @InjectVariable
   - @PostConstruct

3. [Scoping](#scoping)
   - Global Scope
   - Thread Scope
   - Local Scope
   - Request Scope

4. [Core Concepts](#core-concepts)
   - Object Network and Association
   - Transparent Proxy Model
   - Constructor and Field Injection

5. [Request Scope](#request-scope)
   - Understanding Request Scope
   - Example Use Cases
   - Benefits of Request Scope

6. [Initialization with @PostConstruct](#initialization-with-postconstruct)
   - Purpose and Benefits
   - Single @PostConstruct Method
   - MultiplePostConstructMethodsException

7. [Usage Guidelines](#usage-guidelines)
   - Enforcing Encapsulation
   - Controlled Scope
   - Object Lifecycle Management

8. [Advanced Topics](#advanced-topics)
   - Circular Dependencies
   - Profiles
   - Handling Errors and Exceptions
   - Background Metadata Warmup

## Introduction

Welcome to OOPDI, a lightweight and versatile Dependency Injection (DI) framework designed to simplify object management and promote best practices in software development. OOPDI empowers developers to efficiently manage dependencies, enforce encapsulation, and streamline the initialization process of objects within their applications.

### Overview

Dependency Injection is a fundamental concept in modern software development, promoting loose coupling, testability, and maintainability of code. OOPDI manages object instantiation, field injection, constructor injection, scoping, and lifecycle callbacks transparently via Byte Buddy proxies — without requiring any changes to how you write or call your classes.

### Features

OOPDI offers the following features:

- **Annotations**: `@Injectable`, `@InjectInstance`, `@InjectSet`, `@InjectVariable`, and `@PostConstruct` to define and configure dependencies.

- **Scoping**: Fine-grained lifecycle control with four built-in scopes: Global, Thread, Local, and Request.

- **Transparent Proxy Model**: Every managed object is accessed through a Byte Buddy subclass proxy. Scope resolution, lazy creation, and request-scope lifetime management happen inside the proxy interceptor without any caller involvement.

- **Constructor and Field Injection**: Dependencies can be satisfied either via constructor parameters or via annotated fields, with cycle detection for constructor injection.

- **Variable Injection**: Inject environment variables and system properties directly into fields via `@InjectVariable`.

- **Profile Support**: Activate different implementations of a type per environment using profiles on `@Injectable`.

- **Initialization with @PostConstruct**: Run a designated initialization method after a bean is fully constructed and all fields are injected.

## Annotations

### `@Injectable`

Marks a class as managed by the framework. Only classes annotated with `@Injectable` can be instantiated and injected.

```java
@Injectable(scope = Scope.GLOBAL, immediate = false, profiles = {})
public class MyService { ... }
```

Attributes:

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `scope` | `Scope` | `GLOBAL` | Defines the lifecycle scope of the instance (see [Scoping](#scoping)). |
| `immediate` | `boolean` | `false` | If `true`, the real object is created eagerly when the proxy is first registered rather than on the first method call. Only valid with `GLOBAL` scope. |
| `profiles` | `String[]` | `{}` | If non-empty, the class is only active when at least one of the listed profiles matches the active profiles passed to the `OOPDI` constructor. A class with no profiles is always active. |

### `@InjectInstance`

Injects the managed instance of the field's declared type into the field. The framework resolves the concrete class to use by scanning the classpath for non-abstract `@Injectable` subclasses of the field type that match the active profiles. Exactly one match must be found.

```java
@InjectInstance
private MyService myService;
```

### `@InjectSet`

Injects a `Set` containing one managed instance per concrete `@Injectable` subclass of the hint type that matches the active profiles. Addresses Java's type erasure: the actual element type is specified via the `hint` attribute since generic type parameters are not available at runtime.

```java
@InjectSet(hint = MyInterface.class)
private Set<MyInterface> implementations;
```

### `@InjectVariable`

Injects a value from an external source (environment variable or system property) into a field. The framework reads the value at field-injection time. If the key is not found in the specified source, a `RuntimeException` is thrown with a descriptive message.

```java
@InjectVariable(key = "DB_URL", source = VariableSource.SYSTEM)
private String dbUrl;

@InjectVariable(key = "db.username", source = VariableSource.PARAMETER)
private String dbUsername;

@InjectVariable(key = "pool.size", source = VariableSource.PARAMETER)
private int poolSize;
```

`VariableSource` values:

| Value | Reads from |
|-------|-----------|
| `SYSTEM` | `System.getenv(key)` |
| `PARAMETER` | `System.getProperty(key)` |

Supported field types for automatic conversion: `String`, `int`/`Integer`, `long`/`Long`, `short`/`Short`, `float`/`Float`, `double`/`Double`. For any other type the raw `String` value is assigned.

### `@PostConstruct`

Designates a single method as the post-construction initializer. The method is called after the object is instantiated and all fields are injected. The method may declare parameters; the framework will inject managed instances for each parameter type, following the same rules as constructor injection.

```java
@PostConstruct
public void init() {
    // runs after all fields are injected
}
```

Exactly one `@PostConstruct` method per class is permitted. Declaring more than one throws `MultiplePostConstructMethodsException`.

## Scoping

Scoping defines how long a real object lives and when a new one is created. Scope is declared on the `@Injectable` annotation. Every managed object is accessed through a proxy; the scope determines what the proxy hands back when a method is called.

### Global Scope (`Scope.GLOBAL`)

One instance is created per `OOPDI` container and shared across all threads. The instance is created on the first method call (or eagerly if `immediate = true`) and reused for the entire lifetime of the container.

```java
@Injectable(scope = Scope.GLOBAL)
public class ApplicationConfig { ... }
```

### Thread Scope (`Scope.THREAD`)

One instance is created per thread per `OOPDI` container. Each thread gets its own instance, isolated from all other threads.

`immediate = true` is not compatible with `THREAD` scope (there is no single instance to create eagerly at startup) and will be rejected at startup.

```java
@Injectable(scope = Scope.THREAD)
public class RequestContext { ... }
```

### Local Scope (`Scope.LOCAL`)

A fresh real instance is created for **every method call** on the proxy. This means that if bean A holds a proxy reference to local-scoped bean B and calls `b.methodX()` followed by `b.methodY()`, each call receives a completely independent instance of B.

This does **not** affect direct Java calls: if a local-scoped bean calls one of its own methods via `this`, no proxy is involved and the same instance handles the call naturally.

`immediate = true` is not compatible with `LOCAL` scope and will be rejected at startup.

```java
@Injectable(scope = Scope.LOCAL)
public class TransientProcessor { ... }
```

### Request Scope (`Scope.REQUEST`)

One instance is created per outermost proxy method call on the current thread, and that same instance is reused for all nested proxy calls within that call chain. When the outermost call returns, the instance is discarded.

"Request" is not tied to HTTP — it maps to any logical unit of work initiated by a single entry into the proxy. `immediate = true` is not compatible with `REQUEST` scope.

```java
@Injectable(scope = Scope.REQUEST)
public class UnitOfWork { ... }
```

## Core Concepts

### Object Network and Association

Every class managed by OOPDI is reachable as part of an object network rooted at the class passed to the `OOPDI` constructor. The framework instantiates beans on demand as the object graph is traversed through injection points. You obtain the entry point via `getInstance`:

```java
OOPDI<RootService> container = new OOPDI<>(RootService.class);
RootService root = container.getInstance(RootService.class);
```

`getInstance` can also be called for any other `@Injectable` type reachable in the graph. The container is the single source of truth for all managed instances.

### Transparent Proxy Model

Every managed object is wrapped in a Byte Buddy subclass proxy when first registered. The proxy intercepts every method call, resolves the correct real instance based on the bean's scope, and delegates to it. Callers never need to be aware of the proxy — they program against the normal class type.

Because the proxy is a subclass, the managed class must not be `final`, and its constructor must not be `private`.

### Constructor and Field Injection

Dependencies can be declared in two ways:

**Constructor injection**: If a class has a single constructor with parameters, the framework resolves each parameter type as a managed bean and passes it at construction time. A class with more than one constructor is rejected.

```java
@Injectable
public class OrderService {
    private final PaymentService payment;

    public OrderService(PaymentService payment) {
        this.payment = payment;
    }
}
```

**Field injection**: Fields annotated with `@InjectInstance`, `@InjectSet`, or `@InjectVariable` are injected after the object is constructed. Field injection traverses the full superclass hierarchy so annotated fields in abstract base classes are also injected.

Both styles can be combined in the same class.

## Request Scope

### Understanding Request Scope

A Request-scoped instance lives for exactly one outermost proxy call chain on the current thread. The framework uses a `ThreadLocal` with a call-depth counter. On every proxy method invocation:

1. If no request scope exists on this thread yet, a new `InstancesState` is created and stored in the `ThreadLocal`.
2. The call depth is incremented.
3. The method executes (potentially triggering further proxy calls that reuse the same `InstancesState`).
4. The call depth is decremented.
5. When the depth returns to zero, the `ThreadLocal` is cleared and the instance is discarded.

All proxy calls within a single outermost call therefore share one instance of the REQUEST-scoped bean.

### Example Use Cases

- **Unit of work**: Accumulate changes within one business operation and discard the state when the operation completes.
- **Contextual data**: Carry request-specific state (e.g. current user, correlation ID) through a call chain without passing it as method parameters.
- **Per-operation caching**: Cache computations that are only valid within one operation and should not bleed into the next.

### Benefits of Request Scope

- **Consistency**: All participants in a call chain see the same instance.
- **Automatic cleanup**: No explicit lifecycle management required — the instance is discarded the moment the outermost call returns.
- **Thread isolation**: Each thread has its own `ThreadLocal`, so concurrent call chains are fully isolated.

## Initialization with @PostConstruct

### Purpose and Benefits

`@PostConstruct` provides a hook for initialization logic that requires all injected fields to already be set. Construction via `new` only runs the constructor; field injection happens afterward. A `@PostConstruct` method is therefore the correct place to validate injected values, open connections, or pre-compute derived state.

### Single @PostConstruct Method

Exactly one method per class may carry `@PostConstruct`. The method may declare parameters — each parameter is resolved as a managed bean and injected at call time, following the same rules as constructor parameter injection.

```java
@Injectable
public class CacheManager {

    @InjectInstance
    private DataSource dataSource;

    @PostConstruct
    public void warmUp() {
        // dataSource is fully injected here
    }
}
```

### MultiplePostConstructMethodsException

If more than one method in a class is annotated with `@PostConstruct`, the framework throws `MultiplePostConstructMethodsException` at initialization time. Ensure only a single method carries this annotation per class.

## Shutdown with @PreDestroy

A single `@PreDestroy` method per class hierarchy (no parameters) declares cleanup logic. Call `oopdi.shutdown()` (or `close()`) once at application teardown: every managed instance is destroyed in reverse creation order, best-effort — a failing cleanup does not prevent the remaining instances from being destroyed; the individual failures are aggregated on the thrown error. Shutdown is idempotent and observable via `oopdi.getShutdownStatus()`. After shutdown has started, no new beans are created anymore: requests fail fast with `ContainerShutdown` instead of producing instances that could never be destroyed again (already-resolved beans stay readable from their scope cache).

REQUEST-scoped beans do not wait for shutdown: they are destroyed when their call chain ends, in reverse creation order with the same best-effort semantics (a failing request-end cleanup is reported without hiding the call's own outcome). LOCAL-scoped beans are call-transient and never cached, so there is nothing to destroy for them — intentionally no `@PreDestroy` support for LOCAL.

## Startup Validation with `validate()`

Call `oopdi.validate()` explicitly at application boot to dry-validate the bean graph reachable
from the container's root class: no constructor runs, no field is set, no lifecycle method fires.
Eligibility, constructor rules, `immediate` configuration, resolvable constructor/field/`@PostConstruct`
dependencies, `@InjectSet` hints including element subgraphs (under the container's active profiles),
variable presence *and* parsability, and lifecycle cardinalities are checked; only loops consisting
solely of constructor edges are reported as cycles (field-only loops resolve at runtime and stay
silent). All problems found are aggregated into a single `CannotInject` so one boot run shows the
whole wiring state; a silent return means the graph would resolve at runtime. Nothing about normal
resolution changes for applications that never call it.

```java
OOPDI<AppConfig> oopdi = new OOPDI<>(AppConfig.class);
oopdi.validate(); // throws CannotInject listing every wiring problem, or returns silently
```

## Usage Guidelines

### Enforcing Encapsulation

Obtain instances only through the `OOPDI` container or through injected proxy references. Never construct managed classes directly with `new` — doing so bypasses scope management, field injection, and post-construction.

### Self-Invocation Bypasses the Proxy

This is standard Java proxy behavior (the same caveat applies to Spring, CDI, etc.), but it is worth stating explicitly: calling `this.someMethod()` from *inside* a managed bean invokes the real object directly, not the Byte Buddy proxy. Scope semantics that are enforced at the proxy layer — a fresh instance per call for `LOCAL`, call-depth tracking for `REQUEST` — do **not** apply to such self-invocations. If a method needs proxy-level behavior on every call (e.g. a fresh `LOCAL` instance), it must be called through an injected reference to itself or through the container, not via `this`.

### Controlled Scope

Choose scopes deliberately:

- Use `GLOBAL` for stateless services and shared configuration.
- Use `THREAD` for per-thread state in multi-threaded applications.
- Use `LOCAL` when you want a fresh instance on every interaction from a collaborator.
- Use `REQUEST` when a consistent instance must be shared across an entire call chain but discarded afterward.

### Object Lifecycle Management

Avoid holding raw references to real objects. Always interact with the injected proxy. This ensures that scope semantics are respected — particularly for LOCAL and REQUEST scopes, where the real object changes between invocations.

### Public API Boundary

The supported public contract consists of the `OOPDI` entry point, the annotations, `DependencyResolutionContext`, the exceptions, and the `MetadataMode`/`ShutdownStatus`/`WarmupStatus` enums. Everything else (notably casts to internal types such as `InternalResolutionContext`, and reflective access to proxies or framework internals) is unsupported territory: it may change without notice, and developers who bypass the public API this way are responsible for keeping their own application consistent. There is deliberately no JPMS enforcement — the boundary is a documented convention, not a technical barrier.

## Advanced Topics

### Circular Dependencies

Constructor injection cycles are detected at instantiation time. If class A's constructor requires B and B's constructor requires A, the framework throws `CannotInject` with a message identifying the cycle.

Field injection does not produce construction cycles because the real object is instantiated before its fields are processed. A circular field-injection graph is therefore safe.

### Profiles

Profiles allow different implementations of the same base type to be active in different environments. Pass active profile names to the `OOPDI` constructor:

```java
OOPDI<Root> container = new OOPDI<>(Root.class, "production");
```

A class with no `profiles` declared is always active. A class with one or more profiles declared is only active when at least one of its profiles matches an active profile. If multiple concrete subclasses of the same type are active after profile filtering, `MultipleClassesLeftAfterFiltering` is thrown. If none are active, `NoClassesLeftAfterFiltering` is thrown.

```java
@Injectable(profiles = {"production"})
public class ProductionDataSource extends DataSource { ... }

@Injectable(profiles = {"test"})
public class InMemoryDataSource extends DataSource { ... }
```

### Handling Errors and Exceptions

| Exception | Cause |
|-----------|-------|
| `NoClassesLeftAfterFiltering` | No non-abstract `@Injectable` subclass found for a requested type after profile filtering. |
| `MultipleClassesLeftAfterFiltering` | More than one concrete `@Injectable` subclass matched after profile filtering. |
| `MultipleConstructors` | A managed class declares more than one constructor. |
| `MultiplePostConstructMethods` | A class declares more than one `@PostConstruct` method. |
| `MultiplePreDestroyMethods` | A class hierarchy declares more than one `@PreDestroy` method. |
| `CannotInject` | A field, constructor, or variable dependency could not be injected — typically wraps `NoClassesLeftAfterFiltering`, `MultipleClassesLeftAfterFiltering`, a constructor cycle, a missing/invalid `@InjectVariable` value, or an eligibility/scope misconfiguration (abstract class, non-`@Injectable` class, `immediate=true` on a non-GLOBAL scope). |
| `NoRequestScopeAvailable` | A REQUEST-scoped bean's method was called outside any proxy call chain (i.e. directly on the real object). |
| `UnderConstruction` | Internal sentinel for constructor cycle detection; surfaced as `CannotInject`. |
| `ContainerShutdown` | A bean was requested after the container's shutdown began. |
| `DestructionFailed` | One or more `@PreDestroy` methods failed during `shutdown()` or at REQUEST-chain end; destruction still completed best-effort and the individual failures are attached as suppressed exceptions. |
| `ClasspathScanFailed` | The classpath itself could not be scanned (unreadable entries, I/O failure) — an infrastructure problem, distinct from `NoClassesLeftAfterFiltering`/`MultipleClassesLeftAfterFiltering`, which report a completed scan with no/ambiguous results. |
| `WarmupFailed` | The background metadata warmup's classpath scan failed and `MetadataMode.WARMUP_FAIL_FAST` is active (see [Background Metadata Warmup](#background-metadata-warmup)). |

### Background Metadata Warmup

By default (`MetadataMode.DISABLED`), every bean's reflective metadata (primary constructor, field injection points, `@PostConstruct`/`@PreDestroy` methods) is inspected fresh on each access. This can be tuned via the `oopdi.metadata.mode` system property:

| Mode | Behavior |
|------|----------|
| `DISABLED` (default) | No caching; every access re-inspects the class via reflection. |
| `METADATA_ONLY` | Metadata is cached, populated lazily on first access. |
| `WARMUP_FAIL_FAST` | Cached, and a background daemon thread eagerly scans the entire classpath for `@Injectable` classes at container startup to pre-populate the cache. If that classpath scan itself fails, the next `getInstance()`/`getContext()` call throws `WarmupFailed`. |
| `WARMUP_LENIENT` | Same background warmup as `WARMUP_FAIL_FAST`, but a failed scan is only logged; the container keeps working via the normal on-demand fallback (useful when a warmup failure must not prevent, say, a web server from starting). |

```powershell
-Doopdi.metadata.mode=WARMUP_FAIL_FAST
```

A single class that fails metadata inspection during warmup (e.g. it violates the "exactly one constructor" rule) is logged and skipped — only a failure of the classpath scan itself is job-fatal. Call `oopdi.getWarmupStatus()` to observe the background job (`NOT_STARTED`, `RUNNING`, `READY`, `FAILED`; always `NOT_STARTED` when warmup isn't active).

## Release Process

Releases are versioned based on [Conventional Commits](https://www.conventionalcommits.org/) on `main`, via a manually triggered GitHub Actions workflow (no pull request involved):

- `fix:` → patch release
- `feat:` → minor release
- Any commit with a `!:` marker or a `BREAKING CHANGE:` footer → major release
- `chore:`, `docs:`, `test:`, `ci:`, `refactor:`, etc. → no release (workflow no-ops if no relevant commit is found since the last tag)

Version is tracked in [oopdi/pom.xml](oopdi/pom.xml) and [oopdi/CHANGELOG.md](oopdi/CHANGELOG.md).

Run the **Release** workflow (`.github/workflows/release-version.yml`, `workflow_dispatch`) from the Actions tab after merging commits to `main`. In a single run it: inspects commit messages since the last `vX.Y.Z` tag, computes the next version, bumps `oopdi/pom.xml` and `oopdi/CHANGELOG.md`, commits and tags directly on `main` (no PR), builds the jar with Maven, publishes it to GitHub Packages, and creates the GitHub Release with the jar attached. The workflow no-ops if no `fix:`/`feat:`/breaking commit is found since the last tag.

## Installation via GitHub Packages

Each release is published to [GitHub Packages](https://github.com/oopexpert/oopdi/packages) under the unchanged coordinate `de.oopexpert.oopdi:oopdi-core`. GitHub Packages does not allow anonymous reads even for public repositories — consumers need a GitHub Personal Access Token with at least the `read:packages` scope.

Add the repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/oopexpert/oopdi</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>de.oopexpert.oopdi</groupId>
        <artifactId>oopdi-core</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

And configure credentials for the `github` server id in `~/.m2/settings.xml` (see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md#13-oopdi-als-dependency-via-github-packages-einbinden) for details).

