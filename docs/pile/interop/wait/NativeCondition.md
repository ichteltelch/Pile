# `NativeCondition`

A `Condition` backed by a plain Object's monitor (`wait`/`notify`), routed through an injectable `WaitService`.

Source folder: `src`. Package `pile.interop.wait`.

Up: [wait index](_index.md) · [interop overview](../../../overview.md).

## What it's for

`NativeCondition` adapts an ordinary Java object monitor to the `Condition`
contract so it can be used wherever Pile expects a waitable condition, while
keeping all the actual blocking inside a `WaitService`. It holds one final field,
`o` (the object whose monitor is used), and never touches `o.wait()`/`o.notify()`
directly — every method delegates to the `WaitService` passed in. Because the
service can be swapped (e.g. for periodic-wakeup debugging, or to repurpose
interruption as a signalling mechanism — see the [wait index](_index.md)),
`NativeCondition` inherits whatever `wait`/`notify` semantics the active service
defines.

It implements `WaitServiceUsingCondition`, so the parameterless `Condition`
methods (`await()`, `signal()`, …) get default bodies that fetch the current
service via `WaitService.get()` and forward to the `WaitService`-aware overloads
documented here. A `WaitService.Dispatch` (e.g. `DebuggableWaitService`) detects
`WaitServiceUsingCondition` instances and calls these `WaitService`-aware methods
rather than its `__`-prefixed raw fallbacks.

## The await/signal mapping (the delta)

Each `Condition` operation maps onto a monitor operation on `o`:

| `NativeCondition` method | delegates to | i.e. monitor op |
|---|---|---|
| `await(ws)` | `ws.wait(o)` | `o.wait()` |
| `awaitUninterruptibly(ws)` | `ws.waitUninterruptibly(o)` | loop on `o.wait()`, re-asserting interrupt at the end |
| `awaitNanos(ws, nanos)` | `ws.waitNanos(o, nanos)` | timed `o.wait`, returns nanos remaining |
| `awaitNanosUninterruptibly(ws, nanos)` | `ws.waitNanosUninterruptibly(o, nanos)` | as above, swallowing interrupts |
| `signal(ws)` | `ws.notify(o)` | `o.notify()` |
| `signalAll(ws)` | `ws.notifyAll(o)` | `o.notifyAll()` |

So `signal`/`signalAll` are plain `notify`/`notifyAll` on the monitor — there is
**no per-waiter queue** as a `ReentrantLock`'s `Condition` would have; a single
monitor backs the condition, exactly the semantics of `synchronized`/`wait`.

The two timed-with-result methods compute their boolean themselves rather than
relying on the service's return value:

- `await(ws, time, unit)` — times `ws.wait(o, time, unit)` with
  `System.nanoTime()` and returns `true` iff time was left over
  (`unit.toNanos(time) - elapsed > 0`), matching `Condition.await(long,
  TimeUnit)`'s "true if it may not have fully elapsed" contract.
- `awaitUntil(ws, deadline)` — if `deadline` is already past it just does an
  interrupt check (`ws.checkInterrupt()`) and returns `true`; otherwise it waits
  `deadline - now` ms via `ws.wait(o, waitTime)` and returns whether the deadline
  has not yet passed.
- `awaitUninterruptiblyUntil(ws, deadline)` — the uninterruptible twin: past
  deadline returns `true` immediately (no interrupt check, since it cannot throw),
  otherwise `ws.waitUninterruptibly(o, waitTime)` then returns whether the
  deadline is still in the future.

## Caveats & gotchas

- **Caller must hold the monitor.** These all funnel into `o.wait()`/`o.notify()`
  (through the service), so the calling thread must be synchronized on `o`, or the
  underlying `wait`/`notify` throws `IllegalMonitorStateException`. `NativeCondition`
  itself does no locking — it only wraps the wait primitive.
- **Spurious wakeups are not handled here.** Like raw monitors, `await` can return
  early; guard it with a predicate loop in the caller (or use `GuardedCondition`).
- **Return-value polarity is "deadline/time not yet elapsed", not "predicate true".**
  The booleans say nothing about why the wait ended — re-check your condition.
- **`awaitUntil` past-deadline does an interrupt check; the uninterruptible variant
  does not** — a deliberate asymmetry (the latter contractually cannot throw
  `InterruptedException`).

## Common tasks

- **Make an object's monitor awaitable through the wait layer:**
  `new NativeCondition(obj)`, then `synchronized(obj) { cond.await(); }` (the
  no-arg form uses `WaitService.get()`).
- **Observe the condition for changes:** call `observable()`, which wraps `this`
  in an [`ObservableCondition`](ObservableCondition.md).
- **Run it under debugging wakeups:** install a `DebuggableWaitService` as the
  thread-local/global default; its `Dispatch` routes back into these methods, so
  `NativeCondition` needs no change.

## Related

- [`ObservableCondition`](ObservableCondition.md) — produced by `observable()`.
- [`GuardedCondition`](GuardedCondition.md) — await-until-predicate-true wrapper.
- [`WrappedCondition`](WrappedCondition.md) — generic condition decorator.
- `WaitService` / `WaitServiceUsingCondition` (same package) — the injectable
  service and the `Condition`-with-`WaitService`-parameter contract this implements.
