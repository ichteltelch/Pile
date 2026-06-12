# `pile.aspect.bracket.DependerBracket`

A [`ValueOnlyBracket`](_index.md) that, while open, makes a [`Depender`](../Depender.md) *extracted from the held value* depend on one or more fixed [`Dependency`](../Dependency.md) objects — wiring the held value into a dependency graph for exactly as long as the value is held.

Source folder: `src`. File: `pile/aspect/bracket/DependerBracket.java`.

Up: [bracket index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md).

## What it is for

A bracket is an effect that endures for as long as a reactive value holds a particular plain value (see the [bracket index](_index.md)). `DependerBracket` is the "this held value should depend on those dependencies" bracket:

- **`open`** → extract a `Depender` from the value (via the supplied `extract` function) and call `addDependency(d, triggerChange)` for the fixed dependencies `d0` and `ds`.
- **`close`** → extract the same `Depender` again and `removeDependency(...)` the same set.

So the dependency **edges run from the extracted `Depender` to the fixed `Dependency` objects**: the value-derived depender is the one that gets recomputed when those fixed dependencies change. (Contrast with the Strong/Weak variants, below, where the direction is reversed.)

The `extract` function **must be deterministic** — `close` re-derives the `Depender` to undo what `open` did, so if `extract` returns a different object the dependencies can never be removed.

## Key methods

- `create(inheritable, extract, triggerChange, d0, ds)` — the only public entry point; the constructor is package-private. Wraps the result in a stuck-bracket detector when `DebugEnabled.DETECT_STUCK_BRACKETS` is on.
- `open` / `close` — the `ValueBracket` contract. Both short-circuit (return without effect) when `value == null`; a null held value simply carries no dependency wiring.
- `doOpen` / `doClose` — the actual `addDependency` / `removeDependency` loops over `d0` (nullable, added first) then every element of `ds`.
- `isInheritable` — returns the configured flag; controls whether the bracket is bequeathed to a successor value (see [`HasBrackets`](_index.md)).
- `openIsNop` / `closeIsNop` → both `false`; `canBecomeObsolete` → `false` (this bracket never self-retires, unlike the weak variant).

## The `holdsLock`-deferral idiom (the central trick)

Both `open` and `close` check whether the `owner` (the value holder) is a [`HasInternalLock`](../HasInternalLock.md) **and currently holds its own lock on this thread**; if so, the wiring is **not done inline** but handed to `ListenValue.DEFER` to run after the locked section unwinds:

```java
if(owner instanceof HasInternalLock && ((HasInternalLock) owner).holdsLock) {
    ListenValue.DEFER.run(->doOpen(value, owner));
} else {
    doOpen(value, owner);
}
```

Why: brackets run while the owner's `mutex` is held (see the [bracket index](_index.md) caveat). `addDependency` / `removeDependency` acquire locks on the *other* value (the `Dependency`). Doing that while still holding the owner's lock would impose a lock order (owner → dependency) that can deadlock against the reverse order taken elsewhere. `ListenValue.DEFER` is a **thread-local FIFO `Deferrer`**; `DEFER.run(...)` queues the action to execute once the current thread leaves the deferred region, by which time the owner's lock is released — so the cross-value locks are taken without nesting. When the owner is not `HasInternalLock` or the lock is *not* held, the wiring runs inline immediately.

This is why `holdsLock` exists on `HasInternalLock` at all: it is a "would wiring here be lock-order-unsafe?" probe.

## Relation to the Strong / Weak variants

All three live in this package and share the identical `value==null` guard, the `holdsLock` → `ListenValue.DEFER` deferral, and the `create` + stuck-detector pattern. They differ in **edge direction** and **reference strength**:

| Bracket | `extract` yields | Edge wired | Reference to the other party |
|---|---|---|---|
| **`DependerBracket`** | a `Depender` (from the value) | extracted depender → fixed `d0`/`ds` (`Dependency`s) | held value supplies the depender; dependencies are fixed fields |
| [`StrongDependencyBracket`](StrongDependencyBracket.md) | a `Dependency` (from the value) | fixed `d0`/`ds` (`Depender`s) → extracted dependency | strong refs to the fixed dependers |
| [`WeakDependencyBracket`](WeakDependencyBracket.md) | a `Dependency` (from the value) | fixed dependers → extracted dependency | **weak** refs to dependers; self-retires when all cleared |

So `DependerBracket` is the mirror image of `StrongDependencyBracket`: Strong makes *fixed external dependers* depend on the value's dependency; `Depender` makes *the value's own depender* depend on *fixed external dependencies*. `WeakDependencyBracket` is `StrongDependencyBracket` with weak depender refs and `canBecomeObsolete==true` (it asks to be removed once its dependers are GC'd). `DependerBracket` holds the fixed `Dependency` objects strongly and never becomes obsolete.

## The `triggerChange` flag

Forwarded straight to `addDependency(d, triggerChange)` / `removeDependency(d, triggerChange)`. It selects whether adding/removing the dependency does so "in a way that triggers recomputation" of the extracted `Depender`. The Strong variant calls the same flag `recompute`; same meaning, just a different field name.

## Caveats & gotchas

- **`extract` must be deterministic and idempotent across the open/close pair.** A non-deterministic extractor leaks dependency edges that `close` cannot undo.
- **Deferred wiring is not synchronous.** When the owner holds its lock at `open`/`close` time, the `addDependency`/`removeDependency` happens *later*, after the lock region unwinds — do not assume the dependency edge exists the instant `open` returns.
- **Null value = no-op wiring.** `open` returns `true` and `close` returns `false` for a null value without touching any dependencies; this is idiomatic, not a missing case.
- **`d0` is just a convenience first element**, allowed to be null, added/removed before the `ds` array; there is nothing special about it semantically.
- The `ds` array is defensively cloned in the constructor, so later mutation of the caller's array does not affect the bracket.

## Common tasks (how to…)

- **Make a value's internal depender track external dependencies while held:** `DependerBracket.create(inheritable, v -> v.theDepender, triggerChange, firstDep, moreDeps)` and add it as a value bracket on the holder.
- **Choose the opposite direction** (external dependers should track a dependency extracted from the value): use [`StrongDependencyBracket`](StrongDependencyBracket.md) (or [`WeakDependencyBracket`](WeakDependencyBracket.md) if those dependers should not be kept alive by the bracket).

## Tech debt / warts

- The commented-out `QueuedValueBracket.getDefaultDependencyQueue.enqueue(...)` lines in both `open` and `close` are a relic of an earlier deferral strategy superseded by `ListenValue.DEFER`; harmless but dead.
- `doOpen`/`doClose` are `public` (and `doOpen` non-private here while `StrongDependencyBracket.doOpen` is private) despite being internal helpers — inconsistent visibility across the three sibling brackets.
- The `@param`/Javadoc references `{@link Dependencies}` (plural, no such type) in several places — a typo for `Dependency`.

## Related

- [`ValueBracket`](ValueBracket.md) — the bracket interface and open/close contract.
- [`StrongDependencyBracket`](StrongDependencyBracket.md) · [`WeakDependencyBracket`](WeakDependencyBracket.md) — the reverse-direction variants.
- [`Dependency`](../Dependency.md) · [`Depender`](../Depender.md) — the two aspect endpoints the edge connects.
- [`HasInternalLock`](../HasInternalLock.md) — the `holdsLock` probe driving the deferral.
- [concepts/transactions.md](../../../concepts/transactions.md) — how dependency edges drive validity propagation.
