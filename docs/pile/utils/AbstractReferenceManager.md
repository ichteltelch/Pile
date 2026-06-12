# `AbstractReferenceManager`

Contract for a manager that owns a `ReferenceQueue` plus a worker thread that polls it and runs each dequeued reference as a `Runnable` cleanup action.

Source folder: `src` (package `pile.utils`).

Up: [utils index](_index.md) · [overview](../../overview.md).

## What it is

`AbstractReferenceManager` is an **interface** (despite the "Abstract" name), declared `extends Supplier<ReferenceQueue<? super Object>>`. It defines the contract behind Pile's weak-reference cleanup: somewhere there is a `ReferenceQueue`, and the `Reference`s registered with that queue are themselves `Runnable`. When a referent becomes weakly reachable and the GC enqueues its `Reference`, a polling thread dequeues it and invokes its `run()` to perform the cleanup. The interface holds the *contract*; the queue, the thread, and the poll loop live in the concrete implementation ([`DefaultReferenceManager`](DefaultReferenceManager.md)).

This is the plumbing under [`WeakCleanup`](WeakCleanup.md), [`WeakCleanupWithRunnable.md`](WeakCleanupWithRunnable.md), and [`WeakIdentityCleanup`](WeakIdentityCleanup.md) — those create the `Reference`-that-is-`Runnable` and register it here.

## Key members

- `runIfWeak(Object o, Runnable r)` — the central registration call: run `r` once `o` becomes weakly reachable (i.e. is about to be GC'd). Implementations build a weak/identity reference to `o` tied to this manager's queue and ensure the worker is running.
- `getQueueAndStartWorker()` — return the `ReferenceQueue` *and* guarantee the polling worker thread is started. This is the method that lazily spins up the drain; callers that need the queue go through here so the queue is never handed out without a live consumer behind it.
- `get()` (default) — the `Supplier` method; simply delegates to `getQueueAndStartWorker()`. So using the manager *as a `Supplier`* also starts the worker.

## The drain mechanism

There is **no separate "poll" entry point on the interface**. The drain is started on demand, not by the act of registering per se but by `getQueueAndStartWorker()` (which `runIfWeak` is expected to call). The model is:

1. A `Reference` subclass that also implements `Runnable` is created for a referent and associated with the manager's `ReferenceQueue`.
2. The GC enqueues that reference when the referent becomes weakly reachable.
3. A dedicated worker **thread blocks on `ReferenceQueue.remove()`**, dequeues each reference, and runs it as a `Runnable` — see `DefaultReferenceManager` (its `workerRunnable`/`worker` fields and `getQueueAndStartWorker`).

So the drain is a **dedicated polling thread**, lazily started the first time the queue is requested, not an on-register synchronous sweep and not a shared executor tick.

## The global standard manager

Static members provide a process-wide default queue/manager:

- `Std()` — returns the standard `Supplier<? extends ReferenceQueue<? super Object>>`, **lazily** creating a `DefaultReferenceManager` (double-checked locking on `__Privates.class`) the first time if none was set.
- `setStd(Supplier)` — install a custom standard supplier (overrides the lazy default). Note it takes a *`Supplier` of a queue*, not an `AbstractReferenceManager`; a `DefaultReferenceManager` qualifies because the interface *is* such a `Supplier`.
- `__Privates.STD` — the `volatile` holder field behind `Std()`/`setStd`.

This is the hook the weak-cleanup helpers use to find "the" queue without threading a manager instance through every call site.

## Thread-safety

- The lazy-init of the global default in `Std()` uses correct **double-checked locking** over a `volatile` field — safe.
- The worker-start and the actual queue draining are the implementation's responsibility (`DefaultReferenceManager` guards them with its own `lock`); this interface only mandates that `getQueueAndStartWorker()` leave a worker running.

## Gotchas

- **Naming:** it is an `interface`, not an abstract class — "Abstract" here means "the abstract contract", and the static `Std()`/`setStd` make it double as a small registry.
- **Cleanups run on the worker thread**, asynchronously, at GC's discretion — never assume timing, and keep the registered `Runnable` cheap and thread-safe (it executes off the thread that registered it).
- The standard queue's element type is `ReferenceQueue<? super Object>`, i.e. it accepts references to anything; type-narrowing is done by the helper that creates the references, not here.

## Template methods for implementors

A concrete manager must supply `runIfWeak` and `getQueueAndStartWorker` (and inherits the `get()` default). The contract: `getQueueAndStartWorker` is idempotent (start the worker only once) and `runIfWeak` registers a reference that, when collected, causes `r.run()` on the worker. The reference subclasses that satisfy "is a `Reference` and a `Runnable`" are the weak-cleanup sibling classes.

## See also

- [`DefaultReferenceManager.md`](DefaultReferenceManager.md) — the concrete queue + worker thread.
- [`WeakCleanup.md`](WeakCleanup.md), [`WeakCleanupWithRunnable.md`](WeakCleanupWithRunnable.md), [`WeakIdentityCleanup.md`](WeakIdentityCleanup.md) — the `Reference`-as-`Runnable` registrants.
