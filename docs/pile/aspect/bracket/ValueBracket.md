# `pile.aspect.bracket.ValueBracket`

The bracket interface: an effect *opened* when a reactive value assumes a plain value and *closed* when it stops holding it (`open(value, owner)` / `close(value, owner)`), with inheritability and a large catalogue of factories/decorators.

Source folder: `src`. File: `pile/aspect/bracket/ValueBracket.java`.

`ValueBracket<E, O>` is parameterized by the **value type `E`** and the **owner type `O`** (the holder, e.g. a `Pile`). A bracket is "opened" when the owner begins holding a value and "closed" when it ceases to. The open/close machinery that actually drives this lives in [`AbstractReadListenDependency`](../../impl/AbstractReadListenDependency.md) (ARLD) — see its Brackets method-group. Values that carry brackets implement [`HasBrackets`](HasBrackets.md); the current-value-only refinement is [`ValueOnlyBracket`](ValueOnlyBracket.md).

See also the package [_index.md](_index.md), the [overview](../../../overview.md), and [concepts](../../../concepts/).

## The open/close contract

Both methods take `(value, owner)` and return a `boolean`, but the booleans mean **different things**:

- **`open(E value, O owner)`** — returns `true` iff the bracket should **remain installed**, `false` iff it has **become obsolete** (and should be discarded). It is *not* a "kept" flag. Most brackets return `true` unconditionally; only those whose `canBecomeObsolete` is `true` ever return `false`.
- **`close(E value, O owner)`** — returns whether the **reference to the value should be kept**. ARLD nulls the stored reference only if **every** open bracket returns `false` from `close`; if any returns `true`, the value reference is retained. So `close` returning `true` does **not** mean "keep the bracket" — it means "keep the value". This is the asymmetry to watch: `open`'s boolean is about the *bracket*, `close`'s boolean is about the *value reference*.

The canonical "keep the value alive" bracket is the `KEEP` constant: `open` is a no-op returning `true`, `close` returns `true` (keep the reference), not inheritable. Use it for simple values that should survive being invalidated so a recomputation can restore them via `PileImpl.getAsync`.

## The mutex-held caveat (don't destroy inside)

`open`/`close` are invoked **while the owner's `mutex` is held** (`ValueBracket.java`, and ARLD Brackets group). Doing anything reentrant or destructive inside them — e.g. `PileImpl.destroy`, anything that re-takes the same lock or other value locks — risks deadlock. The idiomatic fix is to **defer** the work onto a [`SequentialQueue`](../../utils/) via the `queued(...)` family (below) or `defer(Deferrer)`. The whole interface is built around making that deferral convenient.

## Metadata methods (the "shape" of a bracket)

These let ARLD optimize and reason without invoking the effect:

- `isInheritable` — see Inheritability below.
- `openIsNop` / `closeIsNop` — `true` iff that side is a guaranteed no-op (e.g. an open-only bracket's `closeIsNop` is `true`). Lets the framework skip the call.
- `canBecomeObsolete` — `true` iff `open` can ever return `false`. All the built-in factories return `false` here; obsolescence is used by the dependency/depender brackets and decorators.

These are pure-metadata contracts; the javadoc on each is one line, and they are the methods you must implement when writing a bracket by hand.

## Inheritability (`bequeathBrackets`)

A bracket declared **inheritable** is propagated to value-holders *derived* from an existing one. The mechanism is [`HasBrackets.bequeathBrackets(boolean, HasBrackets)`](HasBrackets.md); the `boolean inheritable` argument threaded through nearly every factory below ends up as the value `isInheritable` returns. Non-inheritable is the common default — note the listener brackets (`ownerValueListenerBracket`, `valueListenerBracket`) and `KEEP` are non-inheritable, while the reference-counting constants (`REF_COUNT_BRACKET`, `COLLECTION_REF_COUNT_BRACKET`) are inheritable so the refcount discipline survives derivation.

## Factory catalogue (static)

All factories wrap the result in a [`DeadlockDetectingBracket`](DeadlockDetectingBracket.md) when `DebugEnabled.DETECT_STUCK_BRACKETS` is on — a debug-only decorator, transparent in production builds.

### Generic open/close brackets
- `openOnly(inheritable, open)` — fires only on open; `close` is a no-op returning `false`.
- `closeOnly(inheritable, close)` — fires only on close.
- `make(inheritable, open, close)` — both sides.

Each comes in a **two-arg `<E,O>` `BiConsumer`/`BiPredicate` form** and a **one-arg `<E>` `Consumer`/`Predicate` form that returns a [`ValueOnlyBracket<E>`](ValueOnlyBracket.md)** (owner ignored). For the `<E>` forms there are extra `closeOnly`/`make` overloads taking a `Consumer` close (instead of `Predicate`) — these always return `false` from `close` (i.e. don't keep the value). Watch the overload resolution: a `Consumer` close vs a `Predicate` close differ only in whether you care about the keep-the-value return.

### Listener brackets
- `ownerValueListenerBracket([inheritable,] ValueListener l)` — adds `l` as an eager `ValueListener` on the **owner** while open (and calls `l.runImmediately(true)` on open); removes on close. Non-inheritable by default. (The javadoc carries a `TODO: Is there really a use case for that?` on the inheritable overload.)
- `valueListenerBracket(inheritable, extract, l)` — adds `l` to a `ListenValue` **extracted from the value** (`extract` must be deterministic so the listener can be removed again). Null extraction result → no-op.

### Dependency brackets (wire a dependency graph while held)
These delegate to [`StrongDependencyBracket`](StrongDependencyBracket.md) / [`WeakDependencyBracket`](WeakDependencyBracket.md):
- `dependencyBracket([extract,] [recompute,] Depender d0 | Depender... ds)` — makes the depender(s) depend on the value (or on `extract(value)`) while open. `recompute` controls whether add/remove triggers recomputation (default `true`).
- `dependencyBracket_weak(...)` — same, but the dependency edge is weak.
- `dependerBracket([extract,] [triggerChange,] Dependency d0 | Dependency... ds)` — the inverse: makes the **value** (a [`Depender`](DependerBracket.md)) depend on given `Dependency`/`Dependencies` while open (via [`DependerBracket`](DependerBracket.md)). `extract` must be deterministic.

### Reference-counting brackets
- `REF_COUNT_BRACKET` (constant) — increments a `ReferenceCounted` on open, decrements on close; inheritable; `nopOnNull`.
- `COLLECTION_REF_COUNT_BRACKET` (constant) — same per-element over an `Iterable`. **The collection must not be mutated while open**, or refcounts won't balance.
- `queuedRefCountBracket(queue)` / `queuedCollectionRefCountBracket(queue)` — the same, but the decrement runs on the supplied `SequentialQueue` (built via `closeOnly(...).queued(queue).beforeOpening(...).nopOnNull`), so the refcount drop happens off the mutex.

### `revalidateBracket(Pile)`
`revalidateBracket(toRevalidate)` — calls `toRevalidate.revalidate` on both open and close. Non-inheritable.

## Decorator catalogue (default methods)

Each returns a new wrapping bracket:

- **`queued(...)`** — wrap in a [`QueuedValueBracket`](QueuedValueBracket.md) so open/close run on a `SequentialQueue`, off the owner's mutex. Overloads: `(q, filter, keep, remain)`, `(q, filter)`, `(q)`, plus `(name, …)` forms that **create a new `SequentialQueue`** (warning: each queue may spin its own thread — reuse queues across brackets) and no-arg forms that use `QueuedValueBracket.getDefaultQueue`. The bare `queued(q)` filters out null values. This is the primary tool for the mutex-held caveat.
- **`defer(Deferrer)`** — wrap in a [`DeferredValueBracket`](DeferredValueBracket.md) deferring the effect via a `Deferrer`.
- **`nonreentrant(Nonreentrant)`** — guard against reentrant open/close via a `Nonreentrant` token ([`NonreentrantBracket`](NonreentrantBracket.md)).
- **`filtered(openFilter[, closeFilter])`** — apply only to matching values ([`FilteredBracket`](FilteredBracket.md)). Convenience: `nopOnNull` / `nopOnNullOpen` / `nopOnNullClose` skip the effect on null values.
- **`beforeOpening(...)` / `beforeClosing(...)`** — prepend extra open / append extra close behavior ([`AugmentedBracket`](AugmentedBracket.md)); `BiConsumer<E,O>` and `Consumer<E>` overloads.
- **`detectStuck` / `dontDetectStuck`** — opt in/out of the [`DeadlockDetectingBracket`](DeadlockDetectingBracket.md) wrapper (the latter is the identity). The factories call `detectStuck` automatically when `DebugEnabled.DETECT_STUCK_BRACKETS`.
- **`filtersFirst`** — returns `this` (identity); a hook overridden by some decorators to reorder filtering ahead of the wrapped effect.

## Caveats & gotchas

- **The two return booleans mean different things** — `open` ⇒ keep-the-*bracket*; `close` ⇒ keep-the-*value-reference*. Easy to conflate. The value reference survives if *any* open bracket's `close` returns `true`.
- **Never destroy/mutate-with-lock inside `open`/`close`** — they run under the owner's mutex. Defer via `queued(...)`/`defer(...)`.
- **`extract` functions must be deterministic** in `valueListenerBracket` / the dependency/depender brackets, or the listener/dependency added on open cannot be removed on close (leak).
- **Mutable collections break refcount brackets** — `COLLECTION_REF_COUNT_BRACKET` and its queued twin assume the iterated collection is stable for the bracket's lifetime.
- **`queued(name)` / no-arg `queued` create or share threads** — a fresh `SequentialQueue` per bracket can mean a thread per bracket; reuse queues.
- **`KEEP` keeps the value, not the bracket** — its `close` returns `true` only to prevent the reference being nulled, enabling restore-by-recompute.

## Tech debt / warts

- Heavy factory **overload fan-out** (many near-identical `dependencyBracket`/`dependerBracket`/`make`/`closeOnly` signatures) makes overload resolution subtle — picking a `Consumer` vs `Predicate` close silently changes the keep-the-value semantics.
- `ownerValueListenerBracket`'s inheritable overload carries an author `TODO` doubting its use case.
- The `open`/`close` return-value semantics are easy to misread and are only documented in the method javadoc, not enforced.

## See also

- [`HasBrackets`](HasBrackets.md) — the aspect that installs/inherits brackets (`_addValueBracket` / `_addOldValueBracket` / `_addAnyValueBracket` / `bequeathBrackets`).
- [`ValueOnlyBracket`](ValueOnlyBracket.md) — current-value-only refinement (the `<E>` factories return this).
- [`DependerBracket`](DependerBracket.md), [`StrongDependencyBracket`](StrongDependencyBracket.md), [`WeakDependencyBracket`](WeakDependencyBracket.md) — the dependency-wiring brackets.
- [`AbstractReadListenDependency`](../../impl/AbstractReadListenDependency.md) — drives open/close under the mutex (Brackets method-group).
