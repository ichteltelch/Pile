# `DefaultReferenceManager`

The standard `AbstractReferenceManager`: one `ReferenceQueue` plus a single daemon worker thread that blocks on it and runs each enqueued `Reference` (which is itself a `Runnable`).

Source folder: `src`. Package `pile.utils`.

This is a short **delta** over [`AbstractReferenceManager.md`](AbstractReferenceManager.md) — read that for the contract (`runIfWeak`, `getQueueAndStartWorker`, the `get()` default, and the static `Std()`/`setStd` registry). Below is only what this concrete class adds.

Up: [utils index](_index.md) · [overview](../../overview.md).

## What it adds over the interface

`AbstractReferenceManager` is just an interface (a `Supplier<ReferenceQueue<? super Object>>`). `DefaultReferenceManager` supplies the actual runtime policy:

- **One `ReferenceQueue`** (`rq`) and **one daemon worker thread** (`worker`) that calls `rq.remove()` in a loop, casts each dequeued reference to `Runnable`, and runs it. This is the concrete "poll the queue and run the references" behavior the interface only describes.
- **Lazy start.** `getQueueAndStartWorker` (and thus the inherited `get()`) starts the worker on first use via `startWorker`, double-checked under `lock`. The thread is named `util.memory.ReferenceManager` and is a **daemon**, so it never keeps the JVM alive.
- **`runIfWeak`** delegates straight to [`WeakCleanup.runIfWeak`](WeakCleanup.md), passing `this` as the manager — i.e. it registers a weak reference whose `run()` fires once the referent is GC'd and the reference is enqueued.

## The worker loop and failure policy

The loop body is `workerRunnable`; each task is run through `safeRun`. The error policy is the salient part:

- **`Exception`** from a cleanup task — logged at `WARNING` and **swallowed**; the loop continues. One misbehaving cleanup handler cannot kill the queue.
- **`Error`** — logged at `SEVERE`. Then:
  - `UnknownError`, `InternalError`, `ThreadDeath` are **rethrown without restart** (treated as unrecoverable / not the handler's fault).
  - Any **other** `Error` triggers **self-restart**: `safeRun` spins up a *new* worker thread before rethrowing, so the queue keeps being serviced even though the current thread is dying.
- **`InterruptedException`** from `rq.remove()` is logged ("Who interrupted the ReferenceManager?") and the loop simply continues — interrupts are not an expected shutdown signal here.

When the loop's `finally` runs (thread exiting), it nulls out `worker` only if it is still the current thread, so a restart that already replaced `worker` is not clobbered.

## Shared / global instance

There is no singleton field on this class. The **shared global manager** is held by the interface's static registry: `AbstractReferenceManager.Std()` lazily creates a `DefaultReferenceManager` (double-checked under `__Privates.class`) unless a host has pre-installed one via `setStd`. So "the default global reference manager" is *a* `DefaultReferenceManager` reachable through `AbstractReferenceManager.Std()`, and that is what `WeakCleanup`'s no-arg constructor wires up. See [`AbstractReferenceManager.md`](AbstractReferenceManager.md) for the registry contract.

## Caveats & gotchas

- **Unchecked cast.** `rq.remove()` is cast to `Runnable` unconditionally. Anything registered on this queue that is *not* a `Runnable` reference would throw `ClassCastException` (caught by `safeRun`, logged, and ignored). In practice everything enqueued here is a `WeakCleanup`/`Runnable`, so this is an invariant, not a guard.
- **No graceful shutdown.** There is no stop/close method; the daemon thread lives for the JVM's lifetime. Fine for a process-global cleanup service, but it cannot be torn down (e.g. in a redeployable container).
- **Restart path is terse.** The `Error`-recovery branch creates the replacement thread but does **not** set its name (unlike `startWorker`), so a restarted worker runs unnamed.
- **`startWorker`'s outer `if(worker!=null) return;` is an unsynchronized fast-path read of a `volatile` field** — correct (the real start is double-checked under `lock`), just note it is intentionally racy as an optimization.

## Tech debt / warts

- Cleanup tasks all run on **one** thread, serially. A slow handler blocks all subsequent cleanups. There is no parallelism or per-task timeout.
- Logger is created with a bare string name `"DefaultReferenceManager"` rather than the class's canonical name.
- The error taxonomy (which `Error`s warrant restart vs. rethrow) is hardcoded and somewhat ad hoc.
