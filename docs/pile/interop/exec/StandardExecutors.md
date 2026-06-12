# `pile.interop.exec.StandardExecutors`

The injectable `ExecutorService`s Pile uses to run jobs on other threads (unlimited pool, scheduled/`delayed` pool, `ForkJoinPool`-backed `limited`) plus thread-factory, interrupt, and `safe`/`parallel`/`joinAll` helpers.

Source folder: `src`. File: `pile/interop/exec/StandardExecutors.java`. All members are `static` — this is a configuration/utility hub, not an instantiated object.

Up: [interop index](../_index.md) · [overview](../../../overview.md).

## What it's for

Pile never spawns threads directly; it routes off-thread work through three named, swappable executors held in `static volatile` fields. To adapt Pile to a host runtime (a custom thread pool, a virtual-thread executor, a single shared pool…), call the `set*` methods **at startup**, ideally **before the `Piles` class is loaded** (the javadoc notes this for maximum performance). This is the "injectable dependencies" pattern the `README` describes; the wait/interrupt half lives in [`WaitService`](../wait/WaitService.md).

## The three executors

Each has a lazy getter (double-checked locking on `StandardExecutors.class`) that creates a default on first use if none was set, and a `set*` setter.

- **`unlimited()`** — unbounded parallelism. Default (`createDefaultUnlimited`) is a `ThreadPoolExecutor` with core 0 / max `Integer.MAX_VALUE`, a `SynchronousQueue`, 1 s keep-alive, the `DEFAULT_THREAD_FACTORY`, and an `afterExecute` override that logs any uncaught `Throwable`. Set via `setUnlimited`. This is the pool used for **off-thread recompute with `delay == 0`** and by the `parallel(...)` helpers.
- **`delayed()`** — a `ScheduledExecutorService` for time-delayed execution with (effectively) unlimited parallelism. Default (`createDefaultDelayed`) is a `ScheduledThreadPoolExecutor` with core 0, max pool 64, 1 s keep-alive, the `DEFAULT_THREAD_FACTORY`. Set via `setDelayed`. Used for **delayed recompute (`delay > 0`)**.
- **`limited()`** — bounded parallelism. Default (`createDefaultLimited`) is `ForkJoinPool.commonPool()`. Set via `setLimited`. (Provided for callers who want CPU-bound throttling; the core recompute paths use `unlimited`/`delayed`, not this.)

`setDelayedAndUnlimited(ScheduledExecutorService)` points **both** `unlimited` and `delayed` at one scheduled executor — convenient when you want a single shared pool for all off-thread work.

> Note the asymmetry: `unlimited` and `delayed` lazily self-initialise to bespoke pools, but `limited` defaults to the JVM-wide common pool. Tasks submitted to `limited` therefore share the `ForkJoinPool.commonPool()` with the rest of the application unless you override it.

## The thread factory

`DEFAULT_THREAD_FACTORY` (public `static final`) builds plain `Thread`s in `defaultThreadGroup`, names them `DefaultFactoryThread-N` (`threadCounter` is a process-wide `AtomicInteger`, so numbering is shared across all default pools), and installs an uncaught-exception handler that logs. `setDefaultThreadGroup(ThreadGroup)` changes the group for **subsequently created** threads (it does not retag live threads, and pools created before the change keep their factory's captured group). `defaultThreadGroup` initialises to the thread-group of whatever thread loads this class.

Wart: `getDefaultThreadGroup()` is an **instance** method on an otherwise all-static class — you must have an instance to call it, even though the field it reads is static. The setter is static. (See Tech debt.)

## Interrupt helpers — delegate to `WaitService`

These do **not** call `Thread` directly; they go through the injectable [`WaitService`](../wait/WaitService.md) so interrupt semantics stay swappable:

- `interruptSelf()` → `WaitService.interruptSelf()` — interrupt the current thread.
- `interrupted()` → `WaitService.isInterrupted()` — **test** the current thread's interrupt status **without clearing it** (unlike `Thread.interrupted()`, which clears; despite sharing the name). Gotcha.
- `checkInterrupt()` / `checkInterrupt(boolean yield)` → `WaitService.checkInterrupt()`, throwing `InterruptedException` if interrupted; the `boolean` overload calls `Thread.yield()` first.

## `safe` — isolate a `Throwable`

- `safe(Runnable)` runs `r.run()`, catching and logging any `Throwable` (at `INFO`), returning the caught `Throwable` or `null` on success. A `null` argument is a no-op returning `null`.
- `safe(Supplier<? extends T>)` runs `r.get()`, returning its result, or `null` if it threw (logged) or the supplier was `null`.

Use `safe` to run untrusted/optional callbacks (e.g. user brackets, listeners, the synchronous job in `parallel(...,sync)`) without letting an exception escape and abort the surrounding operation. It swallows everything including `Error` — deliberate isolation, not a bug, but be aware it will hide `OutOfMemoryError`/`AssertionError` too.

## Parallel-fan-out helpers

- `parallel(Runnable...)` / `parallel(Collection)` — submit each non-null `Runnable` to `unlimited()` and block until all finish via `joinAll`. If one fails, the rest are cancelled and the cause is rethrown.
- `parallel(Collection, Runnable sync)` — additionally runs `sync` **on the calling thread** (wrapped in `safe`, so its failure is swallowed and logged, **not** propagated — asymmetric with the pooled jobs, whose failures do propagate).
- `joinAll(Collection<Future>)` / `joinAll(Future...)` — `get()` each future; on `ExecutionException`, `throwCause` rethrows the cause; in a `finally`, **all** futures are `cancel(true)`-ed (including already-completed ones — harmless, but note every job is cancelled on the way out, success or failure).
- `throwCause(Exception)` — unwraps and rethrows a wrapped cause if it is a `RuntimeException` or `Error`; throws `IllegalArgumentException` if the cause is `null` or a checked exception (it never returns normally despite the `<T>` return type — a throw-helper idiom).

## Where Pile uses these

- **Threaded / delayed recompute** is wired in [`AbstractPileBuilder`](../../builder/AbstractPileBuilder.md) `build`: a `delay == 0` off-thread recomputer takes `StandardExecutors.unlimited()` and a `delay > 0` one takes `StandardExecutors.delayed()` (unless a custom `pool(...)` was set on the builder). See that doc's executor-selection notes. The recomputers then `submit`/`schedule` onto the chosen executor and hand the resulting `Future` to `Recomputation.setThread` so the run can be interrupted on cancel.
- **Off-thread recompute** at the `PileImpl` level ([`PileImpl`](../../impl/PileImpl.md)) runs on whichever executor the installed recomputer captured — i.e. these same pools, indirectly via the builder.
- The `safe` / interrupt helpers are used throughout (`SequentialQueue`, `Suppressor`, `ValueListener`, `Recomputation(s)`, rate-limited listeners, deadlock-detecting brackets) wherever a callback must be isolated or interruption checked.

## Common tasks

- **Swap in your own pools:** call `setUnlimited` / `setDelayed` / `setLimited` (or `setDelayedAndUnlimited`) once at startup, before touching `Piles`. After that, every builder that doesn't set an explicit `pool(...)` uses yours.
- **Use one pool for everything:** `setDelayedAndUnlimited(myScheduledExecutor)` — but note delayed recompute needs a `ScheduledExecutorService`, so the shared pool must be one.
- **Run jobs in parallel and join:** `StandardExecutors.parallel(tasks)`; for fail-fast-with-cancellation over your own futures, `joinAll(futures)`.
- **Run a risky callback without it propagating:** wrap it in `safe(...)`.

## Caveats & gotchas

- **`interrupted()` does not clear** the interrupt flag, unlike `Thread.interrupted()` with the same name.
- A builder with a **positive delay** and a custom non-scheduled `pool` fails with `ClassCastException` (the cast to `ScheduledExecutorService` happens in `AbstractPileBuilder`, not here) — but the *type* mismatch originates because `delayed()` is contractually a `ScheduledExecutorService` while `unlimited()`/`limited()` are not.
- **Set executors before `Piles` is loaded** for "maximum performance" (javadoc) — once defaults are lazily created and captured by recomputers, a later `set*` only affects newly built recomputers.
- `safe` swallows **all** `Throwable`s including `Error`; `parallel(...,sync)`'s synchronous job is `safe`-wrapped and so its failure is silently logged, while the pooled jobs' failures propagate — an intentional but easy-to-miss asymmetry.
- `joinAll` cancels **every** future in its `finally`, even on the success path.

## Tech debt / warts

- `getDefaultThreadGroup()` is a non-static method reading a static field — should be static; calling it requires an otherwise-pointless instance.
- `threadCounter` is shared across all default pools, so thread names interleave across `unlimited` and `delayed` rather than numbering per-pool.
- `limited()` defaulting to the global `ForkJoinPool.commonPool()` while the other two get bespoke pools is an inconsistency worth knowing.
- The `set*` fields are `volatile` but the getters' default-creation isn't idempotent across a racing `set*` call (a `set*` between the null-check and assignment can be overwritten) — benign in the intended "configure once at startup" usage.

## Related

- [interop index](../_index.md) · [`WaitService`](../wait/WaitService.md) (the wait/interrupt side) · [`AbstractPileBuilder`](../../builder/AbstractPileBuilder.md) (executor selection for recompute) · [`PileImpl`](../../impl/PileImpl.md) · [overview](../../../overview.md) · [concepts](../../../concepts/).
