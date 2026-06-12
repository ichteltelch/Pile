# `DefererImpl`

The standard `Deferrer` implementation: a counter-gated queue runner (note the spelling — one `r`). Source folder: `src`.

A short delta over [`Deferrer`](Deferrer.md). For the interface contract (what `run`/`suppressRunningImmediately`/`isDeferring` mean) read that first; this page covers only *how* `DefererImpl` realizes it. Up: [defer index](_index.md) · [overview](../../../overview.md).

## State (how it tracks pending work)

Two `int` counters plus the backing queue (`q`, a [`DeferrerQueue`](DeferrerQueue.md) — FiFo / LiFo / Dedup strategy):

- `shouldBeDeferring` — a **suppression depth**. `> 0` means "defer; don't run anything now." Incremented by `__incrementSuppressors`, decremented by `__decrementSuppressors`; each `suppressRunningImmediately()` hands back a `Suppressor` that decrements on close. This is the nesting counter: N nested suppressors ⇒ value N.
- `hasStartedRunningDeferred` (`volatile`) — a **reentrancy guard**, effectively 0/1. Set while `runDeferredIfNotDeferring` is actively draining, so a nested call returns immediately rather than draining the same queue twice.
- `q` — the actual list of pending `Runnable`s. Nesting/suppression is the counter; the *work* lives in the queue.

There is no separate "is currently running" flag beyond `hasStartedRunningDeferred`; the queue's emptiness is the other half of the gate.

## When it flushes — `runDeferredIfNotDeferring`

The drain loop runs only while **all three** hold: `shouldBeDeferring == 0`, `hasStartedRunningDeferred == 0`, and the queue is non-empty. It is called from every edge that could make the queue runnable: after `enqueue` in `run`, before/after the immediate execution in `run`, and from `__decrementSuppressors` once the suppression depth falls to 0.

Inside, it sets the guard (`hasStartedRunningDeferred++`), then polls and runs items one at a time via `StandardExecutors.safe(r)` (so a throwing task does not abort the drain). The loop re-checks `shouldBeDeferring == 0` on **every** iteration, so a task that opens a new suppression *mid-drain* stops the flush immediately — pending items stay queued for the next decrement.

### The decrement-on-empty dance (salient/surprising)

`runDeferredIfNotDeferring` drops the reentrancy guard the moment it observes the queue went empty (`q.isQueueEmpty()` right after a poll), tracked by the local `decremented`, rather than only in the `finally`. The `finally` then decrements only if that early drop didn't happen. The intent is to reopen the deferrer for reentrant scheduling as early as possible (a task enqueued by the last task can be picked up) while never double-decrementing. This is subtle — read the method body in `DefererImpl` before touching it; off-by-one here corrupts the guard.

## `run(Runnable r)`

- If currently deferring (`isDeferring()`): `enqueue(r)` then `runDeferredIfNotDeferring()` (a no-op flush while suppressed) and return — the task waits for the eventual decrement.
- Otherwise: flush any backlog first, then execute `r` immediately via `StandardExecutors.safe`, and flush again in a `finally`. So even in immediate mode, `run` drains the queue around the call.

Note `isDeferring()` here tests **only** `shouldBeDeferring > 0` — the commented-out `|| hasStartedRunningDeferred()` is deliberately disabled in the base class (see Caveats).

## Reentrancy

Designed to be reentrant on one thread: the `hasStartedRunningDeferred` guard means a `run`/flush triggered from inside a deferred task does not start a second nested drain — it enqueues (or returns) and lets the outer drain loop pick the work up. The per-iteration `shouldBeDeferring` re-check lets a task suspend the flush by opening a suppressor.

## Thread-safety

**The base `DefererImpl` is NOT thread-safe.** The counters are plain `int` (only `hasStartedRunningDeferred` is `volatile`, and that alone does not make the inc/dec/compound checks atomic). Use it confined to one thread, or wrap it.

`makeSynchronized(Object monitor)` returns an anonymous subclass that wraps `enqueue`, `pollQueue`, `isQueueEmpty`, `runDeferredIfNotDeferring`, `suppressRunningImmediately`, the two suppressor inc/dec methods, and the `isDeferring`/`hasStartedRunningDeferred` reads in `synchronized (mon)`. The monitor defaults to the queue `q` when `null` is passed (`defaultMonitor`). `makeSynchronized` is idempotent for the same effective monitor: it returns `this` when asked to synchronize on the monitor it already uses.

The synchronized variant also **changes a semantic**, not just locking: its `isDeferring()` returns `shouldBeDeferring > 0 || hasStartedRunningDeferred()` — i.e. it *also* reports deferring while a drain is in progress, unlike the base. So under a synchronized deferrer, `run` called during an active flush will enqueue rather than execute immediately. Keep this difference in mind when reasoning about ordering.

## Common tasks

- **Get a deferrer:** `Deferrer.wrap(queueSupplier)` → `Supplier<Deferrer>` building `DefererImpl`s; `Deferrer.makeThreadLocal(queueSupplier)` for a per-thread one (`ThreadLocalDeferrer`). See [`ThreadLocalDeferrer`](ThreadLocalDeferrer.md).
- **Batch / defer side effects:** hold a `Suppressor` from `suppressRunningImmediately()` across the batch; closing it (depth → 0) flushes.
- **Choose ordering:** pick the `DeferrerQueue` strategy — `FiFo`, `LiFo`, or `Dedup` (collapses duplicate `Runnable`s; `StayMoveToEnd` controls re-enqueue position). See [`DeferrerQueue`](DeferrerQueue.md).
- **Make it concurrent:** wrap with `makeSynchronized(monitor)` (note the altered `isDeferring` semantics above).

## Caveats & gotchas

- **Not thread-safe unless wrapped** with `makeSynchronized`.
- **Two different `isDeferring()` semantics** between base and synchronized subclass (see Thread-safety) — a real behavioral fork, easy to trip over.
- `__decrementSuppressors` **clamps** `shouldBeDeferring` back to 0 if it goes negative (an unbalanced/extra decrement is swallowed). There is a `//TODO: warn if negative` — over-decrementing is silently tolerated, masking suppressor-balance bugs.
- The `DEBUG` flag (compile-time `false`) maintains a `trace` of stack traces per increment for leak-hunting; it is dead in normal builds and `trace` is `null`.
- The early `hasStartedRunningDeferred--` on empty-queue (the `decremented` path) is delicate; reason about it as a whole, never edit one branch in isolation.

## Tech debt / warts

- Commented-out code in two places: the disabled `|| hasStartedRunningDeferred()` in the base `isDeferring`, and the commented inc/dec around the immediate `run` execution — intent is unclear and they encode the base-vs-synchronized semantic split rather than documenting it.
- `//TODO: warn if negative` in `__decrementSuppressors` — unbalanced suppressors are silently clamped.
- Class name misspelling (`DefererImpl`, one `r`) diverges from the `Deferrer` interface; harmless but a known footgun for `find_symbols`.
