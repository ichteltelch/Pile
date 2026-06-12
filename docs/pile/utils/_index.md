# `pile.utils` — package index (Tier 1)

Source folder: `src`.

General-purpose helpers Pile leans on — weak-reference cleanup, identity-keyed caches/comparators, a function/bijection toolkit, single-thread queues, and stream wrappers. These are not reactive-values types themselves; they are the plumbing the core builds on.

Up: [overview](../../overview.md).

## Weak references & cleanup
- [`AbstractReferenceManager`](AbstractReferenceManager.md) — contract for a manager owning a `ReferenceQueue` + a worker thread that polls it and runs each dequeued reference as a `Runnable` cleanup.
- [`DefaultReferenceManager`](DefaultReferenceManager.md) — the standard `AbstractReferenceManager`: a single `ReferenceQueue` + one daemon worker thread that runs enqueued `Runnable` references (with error self-restart).
- [`WeakCleanup`](WeakCleanup.md) — register a `Runnable` to fire on a daemon thread when a given referent is garbage-collected (the workhorse for auto-unregistering listeners). **Gotcha:** the action must not strongly reference the referent.
- [`WeakCleanupWithRunnable`](WeakCleanupWithRunnable.md) — a `WeakCleanup` whose cleanup `Runnable` is a mutable field on the reference itself, so a listener can hold the reference, probe liveness via `get()`, and self-unregister when the referent is GC'd.
- [`WeakIdentityCleanup`](WeakIdentityCleanup.md) — identity-based variant of `WeakCleanup`: equal only when sharing the same (`==`) non-null referent; frozen identity hash; no-op `run()`.

## Identity-keyed helpers
- [`IdentityComparator`](IdentityComparator.md) — a `Comparator` ordering by `System.identityHashCode` (identity, not `equals`); shared `INST`, with a weak per-hash list to break identity-hash collisions into a true total order.
- [`IdentitiyMemoCache`](IdentitiyMemoCache.md) — an identity-keyed memoisation cache with weak keys and weak values (note the misspelled class name).

## Concurrency
- [`ExecutorWithRecentThread`](ExecutorWithRecentThread.md) — a one-method interface exposing an executor's recently-active (kept-warm) worker thread, for thread-affinity/identity checks. (Not itself an `Executor`.)
- [`SequentialQueue`](SequentialQueue.md) — a single-consumer task queue running submitted `Runnable`s in submission order on one (borrowed) worker.
- [`Nonreentrant`](Nonreentrant.md) — a per-thread guard that redirects re-entrant calls to a *fail* branch (drop/throw, never deferred); used to break reactive feedback loops.

## Functional & misc
- [`Bijection`](Bijection.md) — an invertible `Function` (forward + backward pair) used as a codec / two-way transform.
- [`Functional`](Functional.md) — assorted functional-interface helpers, shared no-op/identity/null singletons, and predicate combinators (`not`, `conjunction`/`disjunction`).
- [`StackTraceWrapper`](StackTraceWrapper.md) — an `Exception` carrying a pre-captured stack trace (overrides `fillInStackTrace` to a no-op) so a remembered call site can be logged later.
- [`NonClosingInputStream`](NonClosingInputStream.md) — an `InputStream` decorator whose `close` does not close the delegate (just counts the attempt).
- [`NonClosingOutputStream`](NonClosingOutputStream.md) — an `OutputStream` decorator whose `close` flushes (not closes) the delegate.

## Sub-packages
- [`utils.defer`](defer/_index.md) — the deferral mechanism (queue work to run at a later flush point instead of immediately).
