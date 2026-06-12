# `ThreadLocalDeferrer`

A per-thread `Deferrer`: a `ThreadLocal<Deferrer>` holder that forwards every `Deferrer` call to whatever deferrer the current thread owns.

Source folder: `src`. Package: `pile.utils.defer` (see [_index.md](_index.md), [overview](../../../overview.md)).

## What it's for

Lets one shared `Deferrer` object (a single `ThreadLocalDeferrer` instance) act as a *different* deferral context per thread. Each thread gets its own backing `Deferrer` lazily, so threads never share deferral state. Implements [`Deferrer`](Deferrer.md).

## Thread-local storage + accessor

- The store is the **public final** field `current`, a `ThreadLocal<Deferrer>` built in the constructor with `ThreadLocal.withInitial(make)`.
- The constructor takes a `Supplier<? extends Deferrer>` (`make`). The first time a given thread touches the deferrer, `make.get()` is invoked to mint that thread's `Deferrer`; subsequent calls on the same thread reuse it.
- The accessor is just `current.get()` — there is no nullable "is a deferrer set?" state. **There is no fallback to run-immediately and no scope-entry/`with*`/`MockBlock` API on this class**: the supplier guarantees every thread always has a deferrer. Whether work runs immediately or is queued is decided entirely by the per-thread backing deferrer (e.g. `DefererImpl` / [`DeferrerQueue`](DeferrerQueue.md)), not here.

## Delegation map

Every interface method is a one-line forward to `current.get().<same method>()`: `run`, `suppressRunningImmediately`, `isRunningImmediately`, `isDeferring`, `hasStartedRunningDeferred`, `__incrementSuppressors`, `__decrementSuppressors`. So the actual deferral behavior (entering/leaving a deferral scope, flushing queued work) lives in the backing deferrer per thread — see [`DefererImpl`](DefererImpl.md) (note the one-`r` spelling) and [`DeferrerQueue`](DeferrerQueue.md).

The one method that does **not** delegate:

- `makeSynchronized(Object monitor)` returns `this`. A `ThreadLocalDeferrer` is already inherently thread-confined (each thread sees its own backing deferrer, no cross-thread shared mutable state), so there is nothing to synchronize — wrapping it in a synchronized adapter would be pointless. This is idiomatic, not a missing feature.

## How a thread gets a deferrer

Construction is normally via the factory `Deferrer.makeThreadLocal(Supplier<DeferrerQueue> qs)` on the [`Deferrer`](Deferrer.md) interface, which wraps the queue supplier with `Deferrer.wrap(qs)` (each call builds a fresh `DefererImpl(qs.get())`) and passes that as the per-thread `make` supplier. So each thread's first use builds its own queue + `DefererImpl`. Nobody "pushes" a deferrer onto the thread imperatively — it is created on demand by the supplier the first time that thread asks.

## Caveats & gotchas

- **Lazy, per-thread, never cleaned up.** The `ThreadLocal` has no `remove()` call here; each thread that ever uses the deferrer retains its backing `Deferrer` for the thread's lifetime. On a long-lived thread pool this means a deferrer lingers per worker thread (usually fine — it is the intended "ambient context").
- `current` being `public final` means callers can read/inspect the `ThreadLocal` directly (e.g. `current.get()`), but cannot replace it.
- This class holds **no** scope-entry logic. To understand how a deferral scope is opened/closed and when deferred work flushes, read the backing [`DefererImpl`](DefererImpl.md) / [`DeferrerQueue`](DeferrerQueue.md), not this file.
