# `ExecutorWithRecentThread`

A one-method interface for an executor that can name a *recently-active worker thread* — a worker kept warm briefly after finishing so callers can detect "am I (or some task) running on that executor's thread?".

Source folder: `src` (package `pile.utils`).

Up: [utils index](_index.md) · [overview](../../overview.md). Sibling implementor: [`SequentialQueue`](SequentialQueue.md). Related: [`StandardExecutors`](../interop/exec/StandardExecutors.md).

## What it is

The whole contract is a single accessor:

```java
public interface ExecutorWithRecentThread {
    public Thread getRecentThread();
}
```

It does **not** extend `Executor`/`ExecutorService` — the "executor" part is a description of who implements it, not a base type. An implementor is some executor-like facility that runs tasks on a worker thread and is willing to expose *which* thread is (or was very recently) doing that work.

`getRecentThread()` returns that thread, or `null` when there is currently no warm worker (none ever started, or the idle worker has already exited). The value is a live snapshot: it can flip to `null` (or to a different thread) concurrently with the call.

## Why a "recent" thread, not "current"

The point of the abstraction is **thread affinity / identity checks**, not lifecycle management. A caller asks "is this the thread my work runs on?" to:

- detect re-entrancy or decide whether it may run something inline vs. hand it off;
- attribute liveness/health to the right worker (e.g. a health monitor polls `getRecentThread()` to watch the worker).

Because the implementor keeps the worker **warm for a short idle window** instead of tearing it down after each task, `getRecentThread()` usually still returns the same thread between bursts of work. That gives two benefits the name hints at: avoiding repeated thread spin-up, and preserving thread-affinity for related tasks submitted back-to-back.

## The implementor in Pile: `SequentialQueue`

`SequentialQueue` is the only Pile implementor (it `implements ExecutorWithRecentThread` and also extends `AbstractExecutorService`). It backs the interface with its single worker:

- The worker thread is recorded in the field `queueWorkerThread` when the worker loop starts (set inside the lambda submitted in `SequentialQueue.enqueue`), and `getRecentThread()` simply returns that field.
- **Single thread, not a pool.** A `SequentialQueue` runs at most one worker at a time, so "the recent thread" is unambiguous. (A pool-backed implementor would have to pick *a* representative thread — the interface only promises *one*.)
- **Idle-timeout / warmth window.** When the queue drains, the worker does not exit immediately. It waits via `SequentialQueue.waitTime` (currently ~1000 ms for the first couple of idle polls, then 0 → exit). During that window `getRecentThread()` keeps returning the live worker; once the worker actually returns, `queueWorkerThread` is cleared to `null` (in the empty-queue exit branch and in the worker's `finally`). So the "recent thread" survives a brief lull and disappears after a longer idle.
- **Restart.** A later `enqueue` after the worker exited submits a fresh worker task (possibly a *different* `Thread`, since the backing `ExecutorService` — `StandardExecutors.unlimited()` by default — may hand out another). `getRecentThread()` then reports the new one.

See [`SequentialQueue`](SequentialQueue.md) for the submit/`execute`/`sync` API and the queue lifecycle; this interface only exposes the worker-identity slice of it.

## Thread-safety

- `queueWorkerThread` is `volatile` in `SequentialQueue`, and `getRecentThread()` reads it without synchronization — a deliberate cheap, lock-free snapshot. Callers must treat the result as immediately-stale: the worker may exit (field → `null`) right after the read.
- The interface makes **no** happens-before promise about tasks relative to the returned thread; it is for identity comparison (`== Thread.currentThread()`), not for publishing task results.

## Caveats & gotchas

- **May return `null`** any time there is no warm worker — always null-check. Do not assume a non-null result stays valid; capture it into a local if you need a stable reference for the duration of a check.
- **Not an `Executor`.** Don't expect `submit`/`execute` from this type; reach for the concrete implementor (`SequentialQueue`) when you need to enqueue work.
- The "recent" semantics are implementation-defined. The interface fixes no minimum warmth duration; `SequentialQueue`'s window comes from its own `waitTime` policy and can change without affecting the contract.

## Common tasks

- **Check whether I'm on the executor's worker thread:** `exec.getRecentThread() == Thread.currentThread()` (null-safe: a `null` recent thread can never equal the current one, so the comparison is already correct). `SequentialQueue` also offers `isQueueWorkerThread()` for its own case.
- **Watch the worker for liveness:** poll `getRecentThread()` and act on the returned `Thread` (its state, stack, interrupt), tolerating `null`.

## Tech debt / warts

- The contract is thin: `null` meaning, warmth duration, and single-vs-pool are all left to the implementor, with no Javadoc on the interface to pin them down. A reader must consult the concrete type (here `SequentialQueue`) to know what "recent" actually means.
- The name suggests an `Executor` relationship that the type does not declare; the connection is by convention only.
