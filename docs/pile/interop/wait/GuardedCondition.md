# `GuardedCondition`

A `Condition` that pairs a backing condition with a boolean guard predicate, so awaiting loops until the guard is true (await-until-the-guard-holds) and signalling can be made conditional on it.

Source folder: `src` · package `pile.interop.wait`.

Up: [wait index](_index.md) · [interop index](../_index.md) · [overview](../../../overview.md).
Siblings: [`WrappedCondition`](WrappedCondition.md) · [`ObservableCondition`](ObservableCondition.md) · [`NativeCondition`](NativeCondition.md).

## What it's for

A raw `Condition` (or Pile's [`WrappedCondition`](WrappedCondition.md)) is *eventful* — it tells a waiter only "you were signalled", not "the thing you waited for happened". `GuardedCondition` closes that gap: it knows the **predicate** that defines the event. Every `await*` method spins in a `while(!checker.getAsBoolean())` loop around the backing wait, so a waiter resumes **only** when the guard is actually satisfied (or the timeout elapsed, or it was interrupted). This makes it the idiomatic answer to the classic "wait for a flag to flip" pattern — it folds the loop-around-spurious-wakeups discipline into the condition itself.

It extends [`WrappedCondition`](WrappedCondition.md) and so is a [`WaitServiceUsingCondition`](_index.md): all blocking goes through an injected `WaitService` (`ws.await(back)`, `ws.signal(back)`, …), never raw monitor calls — see [wait index](_index.md).

## Construction

Two fields, both `final`: `checker` (the `BooleanSupplier` guard) and `alwaysSignal` (a flag).

Four constructors:
- `GuardedCondition(Condition back, BooleanSupplier check)` — wrap an existing condition; `alwaysSignal=false`.
- `GuardedCondition(Condition back, BooleanSupplier check, boolean alwaysSignal)` — the canonical form.
- `GuardedCondition(Lock lock, BooleanSupplier check)` / `(Lock lock, BooleanSupplier check, boolean alwaysSignal)` — convenience: derive the backing condition via `lock.newCondition()`.

The lock that the backing condition belongs to is the lock the guard is protected by. The `Lock` constructors only create a condition from it; the caller must still **hold that lock** while calling `await*`/`signal*` (the loop reads `checker` and the underlying `back.await()` requires the lock held). `GuardedCondition` itself stores no lock reference — there is no `signalAll-must-hold` enforcement, the contract is the same as `java.util.concurrent.locks.Condition`.

## Await semantics (the guard loop)

All overrides wrap the inherited single-wait behavior of [`WrappedCondition`](WrappedCondition.md) in a guard loop:

- `await(ws)` — loops `while(!guard) ws.await(back)`.
- `awaitUninterruptibly(ws)` — same loop, but catches `InterruptedException` from each inner wait, keeps looping, and in a `finally` re-asserts interruption once via `ws.interruptSelf(interrupted)`. (Note: the wait used inside is the *interruptible* `ws.await`, caught-and-retried, not `awaitUninterruptibly`.)
- `awaitNanos(ws, nanosTimeout)` — tracks elapsed time from a `System.nanoTime()` start, recomputing remaining budget each iteration; returns `timeLeft` (estimated remaining nanos, may be `<=0` on timeout). Caller distinguishes success/timeout by the sign of the return, as with `Condition.awaitNanos`.
- `await(ws, time, unit)` — delegates to `awaitNanos(...) > 0`.
- `awaitNanosUninterruptibly(...)` — the nanos variant with the same catch-retry-and-reassert-once interruption handling as `awaitUninterruptibly`.
- `awaitUntil(ws, deadline)` — loops while `!guard`, returning `false` as soon as an inner `ws.awaitUntil` reports the deadline passed; returns `true` only when the guard becomes true.
- `awaitUninterruptiblyUntil(ws, deadline)` — `awaitUntil` with catch-retry-and-reassert-once interruption handling.

In every case, if the guard is **already true** on entry, the method returns immediately without blocking (the loop body never runs).

## Signal semantics (conditional signalling)

`signal(ws)` and `signalAll(ws)` forward to `ws.signal(back)` / `ws.signalAll(back)` **only if** `alwaysSignal || checker.getAsBoolean()`. With the default `alwaysSignal=false`, a signal is suppressed unless the guard currently holds — useful because waiters would just re-loop on a premature wakeup anyway, so the signal would be wasted. Set `alwaysSignal=true` to always forward (e.g. when the guard depends on per-waiter state the signaller cannot evaluate, or to force a re-check).

This is a **delta over a plain `Condition`**, whose `signal` is unconditional. A waiter relying on `alwaysSignal=true` to be woken for re-evaluation will *not* be woken under the default if the signaller's view of the guard is false — a gotcha when the guard is per-thread.

## `observable()`

`observable()` returns `new ObservableCondition(this)`, wrapping this guarded condition so signal calls can be observed — see [`ObservableCondition`](ObservableCondition.md). (Equivalently, callers construct `new ObservableCondition(new GuardedCondition(...))` directly.)

## Who creates it

Within the library:
- `LimitedResource` (`pile.aspect.limitedresource`) exposes `available = new ObservableCondition(new GuardedCondition(LR_LOCK.newCondition(), ()->used<max))` — a guard "capacity is free" over the shared `LR_LOCK`.
- `SequentialQueue.awaitTermination` builds an ad-hoc `new GuardedCondition(new NativeCondition(this), this::isTerminated)` and awaits it through its `WaitService` with a timeout.

The pattern is consistent: take a lock-bound (or [`NativeCondition`](NativeCondition.md)-bound) condition, attach a predicate over state the lock protects, and await/signal through a `WaitService`. It is also used by downstream applications for "wait until this app-state flag is set" handshakes.

## Caveats & gotchas

- **The guard is evaluated under the backing lock.** `checker` is called from inside the await/signal methods, which require the lock held; the predicate must read only lock-protected state, or it can observe torn/stale values.
- **Default-suppressed signals.** With `alwaysSignal=false`, `signal`/`signalAll` are no-ops whenever the guard reads false at signal time. If your guard is per-waiter (not visible to the signaller) you almost certainly want `alwaysSignal=true`.
- **`awaitUninterruptibly*` re-assert interruption exactly once** at the end (via `ws.interruptSelf`), regardless of how many interrupts were swallowed during the loop — interrupts are coalesced, not counted.
- **Timeout return is an estimate.** The returned `timeLeft` is recomputed from `nanoTime()` deltas around the guard loop; treat its sign as the success/timeout signal rather than its exact magnitude.

## Tech debt / warts

- The four `awaitNanos*` methods each recompute `now/timeSpentWaiting/timeLeft` in three near-identical places (loop guard, loop body, post-loop); the duplicated arithmetic is easy to skew if edited.
- No reference to the owning `Lock` is held, so the "must hold the lock" contract is entirely by convention — there is no fail-fast if a caller forgets.
