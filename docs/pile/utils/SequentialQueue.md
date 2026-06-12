# `SequentialQueue`

A single-consumer task queue: submitted `Runnable`s run in submission order on one worker, so callers get serialised execution without holding a lock.

Source folder: `src` (package `pile.utils`).

Up: [utils index](_index.md) · [overview](../../overview.md). Siblings: [`ExecutorWithRecentThread`](ExecutorWithRecentThread.md), [`Nonreentrant`](Nonreentrant.md). Wait primitive: [`GuardedCondition`](../interop/wait/GuardedCondition.md).

## What it's for

You have work that must not run concurrently with itself (must be serialised) but you don't want callers to block on a lock or block each other. `SequentialQueue` accepts `Runnable`s and runs them one at a time, in FIFO order, on a single worker. The caller of `enqueue` returns immediately; the work happens later on the worker. This is the classic "actor mailbox" / single-threaded confinement pattern.

It is an `AbstractExecutorService`, so it also plugs in anywhere an `ExecutorService` is wanted (`submit`, `invokeAll`, … all funnel through `execute` → `enqueue`). It additionally implements [`ExecutorWithRecentThread`](ExecutorWithRecentThread.md) (`getRecentThread` returns the current/last worker thread).

## The worker: borrowed, not owned

`SequentialQueue` does **not** own a dedicated thread. It borrows one slot from a backing `ExecutorService` (field `exa`), defaulting to `StandardExecutors.unlimited()` (resolved lazily, the first time a worker is needed — see the no-`exa` constructors). The worker is a single long-running task submitted to that executor; its `Future` is `queueWorkerFuture` and the thread it happens to run on is recorded in the volatile `queueWorkerThread`.

Lifecycle, all driven from `enqueue`:

- **Start (lazy):** the first `enqueue` (when `queueWorkerFuture==null`) submits the worker loop to `exa`. While running, the worker renames its thread to `"SequentialQueue worker: <name>"` and restores the old name in its `finally`.
- **Run:** the loop polls the deque under `synchronized(this)`, then runs each job *outside* the lock via `StandardExecutors.safe(r)`, then runs the optional `afterJob` (also via `safe`). Running outside the lock is what lets other threads `enqueue` concurrently while a job executes.
- **Park / self-terminate:** when the queue drains, the worker waits (`WaitService.wait`) for a bit. `waitTime(nopTimes)` returns `1000` ms for the first few empty polls, then `0`; on the `0` result the worker **exits and clears `queueWorkerFuture`/`queueWorkerThread`** (under lock, guarded by `queueWorkerFuture==self` so a restarted worker isn't clobbered). So an idle queue gives the borrowed thread back to the pool after a short grace period. A later `enqueue` sees `queueWorkerFuture==null` and submits a fresh worker. Override `waitTime` to change the idle grace / never-park policy.

`enqueue` calls `WaitService.get().notifyAll(this)` after adding, which wakes a parked worker so it picks up the new job promptly.

## Ordering guarantee

FIFO over a single `ArrayDeque` (`q`), polled from the front by one worker. Because there is exactly one consumer and `enqueue`/`poll` are both under `synchronized(this)`, jobs run strictly in submission order, one at a time. `trimQueue(maxSize, removeFromFrontNotBack)` can drop jobs from either end to cap backlog; `clearQueue` discards all pending jobs.

## Sync / await helpers (key methods by purpose)

- `enqueue(Runnable)` — the core submit. `execute` (the `ExecutorService` entry) just delegates here.
- `syncEnqueue(Runnable)` — enqueue and block the *caller* until that specific job has run (the job is wrapped so it flips a flag and notifies on completion). The caller polls with a 1000 ms timed wait.
- `sync()` — `syncEnqueue(NOP)`: block until everything queued *so far* has drained (the NOP reaches the front only after all earlier jobs ran). `sync(int count)` repeats this.
- `syncEnqueue(Runnable, int limit)` / `syncOrWaitUntilShorter(int limit)` — return early once the job ran **or** the queue has shrunk below `limit` (back-pressure that doesn't wait for full drain).
- `interrupt()` — interrupt the worker thread (via the `WaitService`).
- `getQueueWorker()` / `getRecentThread()` — expose the worker `Future` / thread.

## Termination & `awaitTermination`

`SequentialQueue` is an `ExecutorService`, so it has the full shutdown protocol:

- `shutdown()` / `shutdownNow()` set the `closed` flag, cancel `queueWorkerFuture` (interrupting the worker), and `notifyAll`. After `closed`, `enqueue` silently drops new jobs (early `return`). `shutdownNow` additionally drains `q` and returns the not-yet-run jobs.
- `isShutdown()` = `closed`.
- `isTerminated()` = `closed && queueWorkerFuture==null && (q==null || q.isEmpty())` — i.e. shut down, no worker running, nothing queued.
- `awaitTermination(timeout, unit)` blocks on a [`GuardedCondition`](../interop/wait/GuardedCondition.md) over `isTerminated` (a `NativeCondition` on `this`), via `ws.await`, then returns `isTerminated()`. The worker's self-clearing of `queueWorkerFuture` is what eventually satisfies the guard; the `notifyAll` in `shutdown`/`shutdownNow` and the worker's own state changes wake the waiter.

## Error handling of a failing task

A job that throws does **not** kill the worker or stop the queue. Each job runs through `StandardExecutors.safe`, which catches every `Throwable`, logs it at `INFO` ("Isolated an error"), and returns it (here the return is discarded). The next job runs normally. `afterJob` is likewise isolated. The worker loop additionally has an outer `catch(RuntimeException|Error)` that logs at `WARNING` as a last resort, but in normal operation `safe` absorbs job failures before they reach it. **Consequence:** the caller never learns directly that an `enqueue`d job failed — check the log, or wrap the job yourself to capture the outcome (`syncEnqueue` waits for completion but still doesn't surface the exception).

## Caveats & gotchas

- **Fire-and-forget failures are silent to the caller.** Job exceptions are caught and logged at `INFO`, never propagated to `enqueue`'s caller (idiomatic isolation, not a bug — but easy to miss).
- **No dedicated thread; worker identity changes.** The worker can park and later resume on a *different* pooled thread. Don't cache `getRecentThread()`; don't rely on `ThreadLocal`s persisting across the idle gap. If you need a stable thread, pass a single-thread `exa`.
- **Worker parks after idle.** With the default `waitTime`, an idle queue releases its borrowed thread after a brief grace period and restarts on the next `enqueue`. Fine functionally, but means there is no persistent "is the worker alive" guarantee between bursts.
- **`syncEnqueue` uses timed polling.** It re-checks every 1000 ms rather than waiting indefinitely, so a spurious miss costs up to a second of latency, not a hang.
- **Construction caveat:** if no `exa` is supplied, `StandardExecutors.unlimited()` is resolved *each time a worker must be (re)started*, not pinned at construction — swapping the global unlimited executor between bursts changes where future workers run. The `ws` (`WaitService`) is pinned at construction instead.

## Tech debt / warts

- `isQueueWorkerThread()` compares `Thread.currentThread()` against `queueWorkerFuture` (a `Future`), so it can never be `true` — it almost certainly meant to compare against `queueWorkerThread`. See SUSPECTED_BUGS.
- `shutdownNow()` does `new ArrayList<>(q)` / `q.clear()` without the `q==null` guard that `clearQueue`/`trimQueue`/`isTerminated` use, so calling it on a queue that has never been `enqueue`d (so `q` is still `null`) would NPE.
- Constructor overload sprawl: eight constructors covering the cross-product of `afterJob` / `exa` / `ws`.
- The empty `try { … } finally { }` around the worker submission in `enqueue` is dead scaffolding.
