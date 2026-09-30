# CLEANCODE.md

Clean-code contract for AI agents working in this repository. These are not suggestions —
code that violates them will be sent back. Each rule states the abstract intent first,
then the concrete shape it takes here.

## 1. Never use `null` as a state signal — use Null Objects

**Intent:** A `null` return forces every caller to know what "missing" means *here*.
A Null Object answers the same messages as the real thing, so callers stay branch-free.

**Shapes in this repo:**
- A nullable *field* means exactly one thing: "not initialized yet" (lazy init only).
- A *getter* never returns `null`. If "absent" is a legitimate outcome, return a shared,
  stateless, immutable Null Object instead.
- A *call site* never branches on `x == null` — except inside the lazy getter itself.

**Examples:** `MetadataWarmup.disabled()` (permanently `NOT_STARTED`, `start()` is a no-op);
`UninitializedContext` (reports `NOT_STARTED`, `shutdown()` is a neutral no-op).

## 2. Probe state polymorphically, never `null`

**Intent:** Ask the object what state it is in; don't interrogate its existence from outside.

**Shapes in this repo:** `context.isUninitialized()` instead of `context == null`;
`context.getStatus()` instead of `if (context != null) … else …`.
The field `OOPDI.context` is assigned once in the constructor (to the Null Object) and
thereafter only ever replaced — it is never `null`, and no code may assume it could be.

## 3. One abstraction level per method (SLAP)

**Intent:** A method body reads as a short list of *steps*, all at the same altitude.
Anything that needs explaining moves into an extracted private method whose name *is*
the explanation.

**Shape in this repo — `GraphValidator.validate(Class)` reads as three steps:**
```java
Traversal traversal = new Traversal(...); // 1. fresh per-run state
visit(rootClazz, traversal);              // 2. traverse the graph
if (!traversal.problems.isEmpty()) {      // 3. aggregate findings or pass silently
    ...
}
```
Three steps, one level; traversal mechanics and message aggregation live one level below.

## 4. Positive conditions, no early returns

**Intent:** Negated guards (`if (!x) … return;`) force the reader to invert logic and
scatter exit points. State the positive case, put the alternatives in `else` branches.

**Shapes in this repo:** `if (isUninitialized()) { … } else { … }` instead of
`if (!isUninitialized()) { … return; }`; `== ACTIVE` with an explicit `else` instead of
`!= ACTIVE` with a guard return. An intentionally empty `if` branch carries a comment
stating why doing nothing is correct (idempotent no-op).

## 5. Small methods with intention-revealing names

**Intent:** The method name answers *why*, the body shows *how*. If a name needs "and",
split it.

**Examples:** `createContext`, `runWarmup`, `rejectRestartAfterShutdown`,
`checkWarmupNotFailedFast`, `awaitCompletion` — each does one thing, each name reads
like a specification sentence.

## 6. Symmetric lifecycle, fail fast — never silently self-repair

**Intent:** Lifecycle operations come in pairs (`startup`/`shutdown`). Using an object
outside its lifecycle is a programming error and must throw a dedicated exception —
the framework must not paper over it with implicit auto-start or remembered flags.

**Shapes in this repo:** pre-start access throws `ContainerNotStarted` (sibling of
`ContainerShutdown`); `shutdown()` before startup is a defined neutral no-op, not a
silently remembered edge case. Null Objects fail loud (`IllegalStateException`) on
calls that must never reach them — silence would hide bugs.

## 7. Defensive copies happen at the entrance — never casually mid-way

**Intent:** A copy made "just in case" in the middle of a call chain signals that nobody
knows who owns the data. Ownership is decided once, at the boundary: the constructor
(or factory) takes the single defensive copy — arrays, sets, lists, all of them — and
everything downstream uses that stable reference directly, without re-copying.

**Shape in this repo:** `OOPDI` clones the `profiles` varargs exactly once in its
constructor into a `final` field that is never exposed; downstream code must rely on
that stable copy instead of re-cloning mid-way (`InjectableFilter` copies immutably
anyway via `Set.of`/`Set.copyOf`).

## 8. Share-nothing-or-share-immutable across threads

**Intent:** Concurrency safety must be visible in the structure, not argued in comments.

**Shapes in this repo:** lazy getters are `synchronized` on the container (reentrant,
so hierarchical calls are safe); published state travels via `volatile` fields;
shared Null Objects (`disabled()`, `UninitializedContext.instance()`) are stateless
and immutable, hence freely shareable; background completion is signaled with a
`CountDownLatch` counted down in `finally`, never with sleep-polling.
