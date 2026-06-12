# `pile.aspect.Dependency`

Source folder: `src`. File: `pile/aspect/Dependency.java`.

The **aspect interface a reactive value implements when other values may depend on it.** It is the read/dependency-target counterpart to [`Depender`](Depender.md) (the value that *does* the depending). `Dependency extends LazyValidatable`, so every dependency is also lazy-validatable. Concrete piles implement it via the assembled contracts in `pile.aspect.combinations` (`ReadDependency` → … → `Pile`) and the `AbstractReadListenDependency` base.

See the [overview](../../overview.md) for where this sits in the architecture, and [concepts/transactions.md](../../concepts/transactions.md) for how dependencies drive validity propagation and the deep-revalidate mechanism.

## What it is for

A `Dependency` exposes the surface other values need in order to (a) register/unregister as dependers, (b) ask whether the value is currently valid (so they know whether to recompute), and (c) participate in deep-revalidation bookkeeping. Most methods are **framework-internal plumbing**, not client API — the ones prefixed `__` carry explicit "do not call this from anywhere else" warnings.

## Key methods by purpose

### Depender registration (internal — `__`)
- `__addDepender(Depender d, boolean propagateInvalidity)` / `__removeDepender(Depender d)` — a `Depender` calls these to (un)register itself. **Warning: callers other than the depender must not invoke these**.
- `giveDependers(Consumer<? super Depender> out)` — iterate the current dependers.

### Validity
- `isValid` — current validity. **May block on internal mutexes and may cause side effects** such as triggering an observed-validity change. If invalid, dependers should not recompute.
- `isValidAsync` — plain status query, **no locking, no triggered actions**. Use this when you must not block or cause effects.
- `autoValidate` — recompute if invalid, recursing over all transitive dependencies; a no-op for subclasses that have no "invalid" state.

### Deep revalidation
- `suppressDeepRevalidation` → returns a `Suppressor` that disables deep revalidation until released. Reified as the static `SUPPRESS_DEEP_REVALIDATION` function.
- `isDeepRevalidationSuppressed` — query that state.
- `__dependerNeedsDeepRevalidate(Depender d, boolean needs)` — register/clear that a depender (or one of its transitive dependers) **has been made valid despite some of this dependency's dependencies being invalid**, and therefore needs a `Depender#deepRevalidate(...)` call when things later happen to this dependency. If `d` is not actually a depender, the call is ignored. This is the registry side of the "valid despite invalid dependencies" interaction — see [transactions § deep-revalidate](../../concepts/transactions.md).
- `__setEssentialFor(Depender value, boolean essential)` — record that this dependency's *essential* status changed for a given depender. **Must only be called by `Depender#setDependencyEssential(...)`**.

### Transactions (read-only view here)
- `isInTransaction` — whether a transaction is currently open on this value.
- `inTransactionValue` — a lazily-initialized reactive `ReadListenDependencyBool` mirroring that state. (The transaction *machinery* lives in `DoesTransactions` / ARLD — see [transactions](../../concepts/transactions.md).)

### Recording reads
- `recordRead` (default method) — registers a read of this dependency in the current `DependencyRecorder`, if a recomputation is recording dynamic dependencies. No-op when there is no active recorder.

### Lifecycle / debug
- `isDestroyed` — whether the value has been destroyed and should no longer be used.
- `dependencyName` — debug-only description; **must never return `null`, returns `"?"` when unnamed**.
- `_printConstructionStackTrace` — if `DebugEnabled.DE`, prints the stack trace captured at construction, to hunt down an untraceable instance.

### Constants
- `NO_DEPENDENCIES` — shared empty `Dependency[]`.
- `SUPPRESS_DEEP_REVALIDATION` — `Function<Dependency, Suppressor>` reification of `suppressDeepRevalidation`.

## Salient / surprising behavior

- **`isValid` is not side-effect free.** It can block on mutexes and trigger an observed-validity change. When you only want a snapshot, call `isValidAsync`.
- **Deep revalidation, defined.** It happens when a value that is *invalid* but transitively has *valid* dependers is **`set`** (not when recomputed); it then revalidates all dependencies. Suppressing it stops both triggering it and propagating the recursion from a triggering sub-dependency. The author flags the propagation half as possibly-wrong: *"TODO: The last bit should maybe behave different?"*.
- **The `__`-prefixed methods are private-by-convention, not by language.** Several carry hard "do not call from elsewhere" warnings — they are part of the depender↔dependency protocol and break invariants if called directly.

## Caveats & gotchas

- Calling `__addDepender` / `__removeDepender` / `__setEssentialFor` / `__dependerNeedsDeepRevalidate` yourself bypasses the protocol; always go through `Depender` (e.g. `Depender#setDependencyEssential`).
- `isValid` may block — do not call it from contexts that must not acquire the value's mutex; use `isValidAsync`.
- The deep-revalidate "propagation" suppression behavior is explicitly marked as uncertain; treat it as subject to change.
- `dependencyName` / `_printConstructionStackTrace` are **debugging aids only**; the latter is a no-op unless `DebugEnabled.DE` is set.

## Common tasks (how to…)

- **Check if a dependency can be safely consumed:** `isValid` (blocking, authoritative) or `isValidAsync` (snapshot).
- **Temporarily disable deep revalidation:** `Suppressor s = dep.suppressDeepRevalidation; try { … } finally { s.release; }` — or use the `SUPPRESS_DEEP_REVALIDATION` function where a `Function<Dependency,Suppressor>` is expected.
- **Force transitive recomputation of an invalid subtree:** `autoValidate`.
- **Observe transaction state reactively:** subscribe to `inTransactionValue`.
- **Make a recomputation record a dynamic read:** call `recordRead` while a `DependencyRecorder` is active (normally done for you by the framework).

## Tech debt / warts

- `suppressDeepRevalidation`'s propagation behavior carries an open `TODO` questioning whether it is correct.
- Heavy reliance on `__`-prefixed "do not call" methods to express access control the language can't enforce.
- Several Javadoc `@return` / `#see` tags are empty or malformed, consistent with the project-wide note that some API is unsystematic (see [overview § caveats](../../overview.md)).

## Related

- [`Depender`](Depender.md) — the counterpart aspect.
- [concepts/transactions.md](../../concepts/transactions.md) — validity propagation and the deep-revalidate registry (`__dependerNeedsDeepRevalidate`).
- [overview.md](../../overview.md) — architecture map.
