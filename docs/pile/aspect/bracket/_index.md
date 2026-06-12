# `pile.aspect.bracket` — package index (Tier 1)

Source folder: `src` (all types below).

**Brackets**: an effect that should endure for exactly as long as a reactive value holds a particular plain value. A [`ValueBracket`](ValueBracket.md) is *opened* when the value is assumed and *closed* when it stops being held; a value that supports them implements [`HasBrackets`](HasBrackets.md). The open/close machinery (current-value / old-value / any-value brackets, transfer between them) lives in [`AbstractReadListenDependency`](../../impl/AbstractReadListenDependency.md). **Caveat:** `open`/`close` run while the value's `mutex` is held — don't do reentrant/destructive things inside them; defer to a `SequentialQueue` if needed.

Up: [aspect index](../_index.md) · [overview](../../../overview.md).

## Core
- [`ValueBracket`](ValueBracket.md) — the bracket interface: `open(value, owner)` / `close(value, owner)` (asymmetric booleans — `open`→keep-bracket, `close`→keep-value-ref), inheritability, a big factory/decorator catalogue. Runs under the owner's `mutex` — defer heavy/destructive work.
- [`HasBrackets`](HasBrackets.md) — the aspect for values that carry `ValueBracket`s: register value / old-value / any-value brackets (which held-value slot the bracket follows) and `bequeathBrackets` the inheritable ones to derived values.
- [`ValueOnlyBracket`](ValueOnlyBracket.md) — a `ValueBracket` whose effect depends only on the held value (owner ignored) — **owner-agnostic, NOT slot-restricted**. The one bracket kind a sealed `SealPile` still accepts.

## Dependency brackets (pull a value into a dependency graph while held)
- [`DependerBracket`](DependerBracket.md) — opens an edge **value-derived depender → fixed dependencies** for the value's lifetime; defers the wiring when the owner holds its lock (avoids lock-ordering deadlock).
- [`StrongDependencyBracket`](StrongDependencyBracket.md) — the mirror (**fixed dependers → value-derived dependency**), holding the dependers by **strong** refs (pins them in memory); never obsolete.
- [`WeakDependencyBracket`](WeakDependencyBracket.md) — like `StrongDependencyBracket` but holds the dependers **weakly** (they may be GC'd); self-retires once no live depender remains.

## Decorators
- [`AugmentedBracket`](AugmentedBracket.md) — decorator running extra behavior before `open` / after `close` (`beforeOpening`/`beforeClosing`); booleans/metadata pass through.
- [`FilteredBracket`](FilteredBracket.md) — applies the wrapped bracket only to values matching a predicate (e.g. `nopOnNull`); `close` re-tests the *open* filter for symmetry.
- [`NonreentrantBracket`](NonreentrantBracket.md) — guards against reentrant open/close on the same thread (skips reentrant calls via a shared `Nonreentrant` token).
- [`DeferredValueBracket`](DeferredValueBracket.md) — defers the open/close *effect* to a `Deferrer` (off the owner's mutex), answering keep/remain synchronously; twin of `QueuedValueBracket` without queue ordering.
- [`QueuedValueBracket`](QueuedValueBracket.md) — runs open/close on a `SequentialQueue` (off the mutex, FIFO order); the `queued(...)` decorator.
- [`DeadlockDetectingBracket`](DeadlockDetectingBracket.md) — debug-only watchdog that logs a stack trace if open/close overruns a timeout (`DETECT_STUCK_BRACKETS`-gated).
