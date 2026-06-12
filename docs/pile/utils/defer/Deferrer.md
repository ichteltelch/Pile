# `Deferrer`

The interface for deferring a `Runnable` to a later flush point — run work *later* / at a safe point instead of immediately.

Source folder: `src`. Package `pile.utils.defer`.

Up: [defer index](_index.md) · [utils index](../_index.md) · [overview](../../../overview.md).
Implementations: [`DefererImpl`](DefererImpl.md) (note the one-`r` spelling), [`ThreadLocalDeferrer`](ThreadLocalDeferrer.md). Queue backing: [`DeferrerQueue`](DeferrerQueue.md). Suppression handle: `Suppressor` (`pile.aspect.suppress`).

## What it's for

A `Deferrer` is the central contract of this package: a place to hand off a `Runnable` so it runs **at a later, safe flush point** rather than synchronously at the call site. In Pile it is used to push listener notifications and other side effects out of the middle of a transaction or recompute, and replay them once the system is in a quiescent state. The `Runnable`s are stored in a backing [`DeferrerQueue`](DeferrerQueue.md); the `Deferrer` owns the *policy* of when the queue drains.

## The core model: a suppression depth

A deferrer is in one of two modes, governed by a **suppression counter** (`shouldBeDeferring` in `DefererImpl`):

- **Running immediately** (depth 0): `run(r)` executes `r` right away (after first draining any pending work).
- **Deferring** (depth > 0): `run(r)` only *enqueues* `r`; it will run later, when the depth returns to 0.

You raise the depth with `suppressRunningImmediately()`, which returns a `Suppressor`; **closing that `Suppressor` lowers the depth again**. The depth is a count, so suppressions nest — work only flushes when the *outermost* one closes. The raw `__incrementSuppressors` / `__decrementSuppressors` are the underlying counter ops (the double-underscore marks them as internal plumbing the `Suppressor` calls; `__decrementSuppressors` is what actually triggers the flush when the count reaches 0 — see `DefererImpl.__decrementSuppressors`).

## The flush point

A **flush point** is the moment the suppression depth drops back to 0 (the last `Suppressor` closes), or any `run(...)` call made while already at depth 0. At that moment the deferrer drains its queue, executing the buffered `Runnable`s. This is the "later, safe point" the whole package exists to provide: callers wrap a region of work in a `suppressRunningImmediately()` block, enqueue side effects during it, and they all fire together when the block ends.

Draining happens via `DefererImpl.runDeferredIfNotDeferring`, which loops polling the queue **as long as the depth is 0 and no drain is already in progress**. Two guards matter:

- It is **re-entrancy safe**: `hasStartedRunningDeferred` flags an in-progress drain so a `run(...)` or another flush triggered from *inside* a deferred task does not start a nested drain loop — it appends to the queue and lets the outer loop pick it up. `hasStartedRunningDeferred()` exposes this state.
- It **re-checks the depth between every task**, so if a deferred task itself raises the suppression depth, draining pauses immediately.

Each task is run through `StandardExecutors.safe(r)` (`pile.interop.exec`), so an exception in one deferred `Runnable` does not abort the rest of the flush.

## Methods by purpose

- `run(Runnable r)` — the one operation clients call. Defer-or-run-now depending on mode.
- `suppressRunningImmediately()` — open a deferral region; returns a `Suppressor` whose close ends the region (and flushes if it was the outermost). This is the idiomatic entry point.
- `isDeferring()` / `isRunningImmediately()` — query the current mode (mutually exclusive).
- `hasStartedRunningDeferred()` — true while a flush/drain is actively running.
- `__incrementSuppressors()` / `__decrementSuppressors()` — internal counter ops behind the `Suppressor`; not for normal client use.
- `makeSynchronized(Object monitor)` — return a thread-safe view guarding queue/counter access on `monitor` (or the queue itself if `null`).

## Factories and constants

- `Deferrer.wrap(Supplier<DeferrerQueue> qs)` → `Supplier<Deferrer>` — adapts a queue supplier into a supplier of `DefererImpl`s, each with a fresh queue.
- `Deferrer.makeThreadLocal(Supplier<DeferrerQueue> qs)` → a [`ThreadLocalDeferrer`](ThreadLocalDeferrer.md): every thread transparently gets its own `DefererImpl` + queue. This is how you get a per-thread deferral context — deferral state on one thread never affects another.
- `Deferrer.DONT` — a **no-op / pass-through** deferrer: `run(r)` always runs `r` immediately, it is permanently in immediate mode, `suppressRunningImmediately()` returns `Suppressor.NOP`, and the counter ops do nothing. Use it where the deferral seam is required by an API but you want no deferral.

## No deduplication or keying by default

Plain `run(...)` enqueues every `Runnable` as-is; ordering and duplicate handling are entirely the **queue's** concern, not the `Deferrer`'s. The choice of `DeferrerQueue` decides this — see [`DeferrerQueue`](DeferrerQueue.md): `FiFo` and `LiFo` keep every submission, while `Dedup` collapses repeated submissions of the *same* `Runnable` (keyed by `Runnable` identity/`equals`+`hashCode`) into one queue entry. So if you want dedup or LIFO replay, you pick it by choosing the queue supplier you pass to `wrap`/`makeThreadLocal`, not via any method on `Deferrer`.

## Caveats & gotchas

- **You must close the `Suppressor`.** If a `suppressRunningImmediately()` handle is never closed, the depth never returns to 0 and queued work never flushes. Use try-with-resources / a `finally`.
- **Dedup keys on the `Runnable` object.** A fresh lambda is a *new* identity each time, so `Dedup` only collapses submissions that pass the *same* `Runnable` instance (or one with matching `equals`/`hashCode`). Lambdas capturing different state are distinct. (See `DeferrerQueue.Dedup` and its `StayMoveToEnd` hook for whether a re-enqueue moves the entry to the tail.)
- **`makeThreadLocal` deferrers are per-thread.** Suppressing immediacy on thread A does not defer work submitted on thread B; each thread flushes independently.
- **`makeSynchronized` on a `ThreadLocalDeferrer` returns `this`** (unsynchronized) — sensible, since a thread-local instance is already only touched by its owning thread, but worth knowing if you wrapped a thread-local expecting a lock.
- **Exceptions in deferred tasks are swallowed-and-continued** via `StandardExecutors.safe`; they will not propagate to whoever closed the suppressor.

## Common tasks

- *Defer a batch of side effects:* `try (Suppressor s = deferrer.suppressRunningImmediately()) { … deferrer.run(effect); … }` — all `effect`s flush when the block exits.
- *Run work immediately regardless:* call `deferrer.run(r)` while not deferring, or use `Deferrer.DONT`.
- *Want a per-thread context:* build with `Deferrer.makeThreadLocal(queueSupplier)`.
- *Want dedup / LIFO:* pass the corresponding `DeferrerQueue` supplier (`DeferrerQueue.Dedup::new` etc.) to `wrap`/`makeThreadLocal`.

## Tech debt / warts

- **Spelling inconsistency:** the interface is `Deferrer` (two `r`) but the standard impl is `DefererImpl` (one `r`). Easy to mistype; the index calls it out.
- The internal counter API leaks onto the public interface as `__incrementSuppressors` / `__decrementSuppressors` (double-underscore convention rather than real encapsulation); clients are trusted not to call them directly.
- `DefererImpl.__decrementSuppressors` clamps a negative count back to 0 with a `//TODO: warn if negative` — an unbalanced close is silently corrected rather than reported.
- `DefererImpl` carries a `DEBUG`-gated `trace` list of suppression stacks (compiled off by default).
