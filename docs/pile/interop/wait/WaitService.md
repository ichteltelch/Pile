# `WaitService`

The injectable abstraction over `wait`/`notify`/`sleep`/`interrupt` (plus `Condition` await/signal) that Pile blocks through, so the host can substitute non-native semantics.

Source folder: `src` · package `pile.interop.wait`.

Up: [wait index](_index.md) · [interop index](../_index.md) · [overview](../../../overview.md).

## What it's for

Pile never calls `Object.wait`, `Thread.sleep`, or `Thread.interrupt` directly when it blocks; it routes everything through a `WaitService`. Making the blocking primitives an injectable dependency lets the host redefine what they *mean*. The `README` ("injectable dependencies") gives two motivating reasons:

- **Debuggability** — a thread parked indefinitely is hard to inspect in a debugger. A `WaitService` can cap each wait/sleep so the thread wakes periodically (every ~1s) and re-parks, without the caller spelling that out. This is exactly what `DebuggableWaitService` does, and it is the **default** (see below).
- **Repurposed interruption** — a program may use `Thread.interrupt` for something other than "abort this thread" (e.g. "wake up, check messages, go back to sleep unless it's a real stop signal"). Injecting the `WaitService` lets Pile adapt to such a signaling regime instead of treating every interrupt as a crash.

The interface also abstracts `java.util.concurrent.locks.Condition` (`await*`/`signal*`), so a condition's waiting behavior is brought under the `WaitService`'s semantics — see `WaitServiceUsingCondition` ([wait index](_index.md)).

## Getting the active service: `get()`

`WaitService.get()` returns the service the **current thread** should use. Resolution order (in `get`):

1. the thread-local override in `WaitServiceConfig.current` (an `InheritableThreadLocal`, so child threads inherit), if set;
2. otherwise `WaitServiceConfig.globalDefault`.

`WaitServiceConfig` is **package-private**; you configure it only through the static methods on `WaitService`:

- `setGlobalDefault(ws)` — replace the process-wide default (non-null).
- `setThreadLocalDefault(ws)` — pin a service for this thread (and inherited children) until changed or until an enclosing `withThreadLocalDefault` block closes.
- `withThreadLocalDefault(ws)` — returns a `MockBlock` (see [Suppressor.md](../../aspect/suppress/Suppressor.md) for the `MockBlock` pattern) that sets the thread-local service on open and **restores the previous one** on close. The preferred scoped form.

The **out-of-the-box default** is `WaitService.DEBUGGABLE_NATIVE` (set as `WaitServiceConfig.globalDefault`) — i.e. native semantics *with periodic wakeups already on*. To get truly raw native blocking, install `WaitService.NATIVE`.

## The built-in implementations

- **`WaitService.NATIVE`** — thin pass-through to `Thread.sleep` / `monitor.wait` / `monitor.notify` / `Thread.interrupt` / `Condition.await`. Anonymous singleton field. Its `noNonstandardInterrupts()` is `MockBlock.NOP` (nothing to suppress — interrupts already mean the native thing). Note `isInterrupted()` here is **not** a pure query: it calls `Thread.interrupted()` (which *clears* the flag) and, if set, re-asserts it via `interruptSelf()` before returning `true` — so it consumes-and-restores rather than peeking.
- **`WaitService.Dispatch`** (interface) — mixes in the `Condition` routing: each public `await*`/`signal*` checks `instanceof WaitServiceUsingCondition` and either dispatches to the `WaitService`-aware overload or falls through to a paired abstract `__`-prefixed method (`__await`, `__awaitNanos`, …) that an implementor supplies for *plain* `Condition`s. This is the seam for adding behavior (like the periodic cap) to condition waits.
- **`WaitService.DebuggableWaitService`** (`implements Dispatch`) — wraps a `raw` `WaitService` and **bounds every wait/sleep/await by `periodicWakeupTime`** (default 1000 ms, settable via `setWakeUpTime` / the `TimeUnit` constructor). It loops `raw.sleep(min(remaining, period))` until the real deadline, and for the unbounded `wait(monitor)` it forwards `raw.wait(monitor, period)`. The `__await*` methods clamp the timeout to the period and recompute the remaining time to return. `DEBUGGABLE_NATIVE` is `new DebuggableWaitService(NATIVE)`. Its `raw` field is public.

## Method families (delta over the javadoc)

The per-method contracts are in the source javadoc; what matters across the surface:

- **Blocking primitives** — `sleep`, `wait(monitor[, millis])`, `waitNanos`, plus `TimeUnit` overloads. `notify`/`notifyAll` for the monitor side.
- **Interruption** — `interrupt(t)`, `interrupt(t, cause)` (cause-carrying overload defaults to plain `interrupt`), `interruptSelf[(cause)]`, `interrupted()` (clear-and-return, the `Thread.interrupted()` analogue), `isInterrupted()`, `checkInterrupt()` (throw if interrupted), `clearInterrupted()`.
- **`Condition`** — `await`, `awaitNanos`, `await(timeout,unit)`, `awaitUntil`, `signal`/`signalAll`, each with a `WaitServiceUsingCondition` overload that simply delegates into the condition (`c.await(this)` etc.).
- **`*Uninterruptibly*` variants** — many are `default` methods implementing the standard "swallow `InterruptedException`, retry until the deadline, then re-assert the interrupt via `interruptSelf` in a `finally`" loop. They are interface defaults, so an implementation inherits them unless it overrides (e.g. `DebuggableWaitService` overrides `sleepUninterruptibly` / `waitUninterruptibly(monitor,millis)` to apply the cap).
- **`noNonstandardInterrupts()`** — returns a `MockBlock`; while open, the thread should not be *natively* interrupted unless the interruption is one the `WaitService` would surface as an `InterruptedException` from its own wait/sleep. Use it to wrap library calls that bail on interrupt when the host has repurposed interrupts for general signaling. `NATIVE` returns `MockBlock.NOP`; `DebuggableWaitService` forwards to `raw`.

## Where Pile uses it

Pile blocks through `WaitService.get()` rather than raw monitors. Key call sites:

- **`PileImpl.getValid`** — when a caller needs a valid value and the value is mid-recompute/in-flux, the wait happens through the active `WaitService` (so the debuggable default wakes periodically instead of parking forever). See [PileImpl.md](../../impl/PileImpl.md).
- **`AbstractReadListenDependency`** `await`-style methods — the shared base's blocking-until-valid / blocking-until-condition logic routes through the service. See [AbstractReadListenDependency.md](../../impl/AbstractReadListenDependency.md).
- `Constant` and `Independent` also reference it for their (trivial / always-valid) blocking paths.

Because this is forward-linked from many docs: the one thing to remember is **Pile waits cooperatively** — substitute a `WaitService` and you change how every Pile blocking wait behaves process-wide (or per-thread).

## Caveats & gotchas

- **The default is not raw.** Pile ships with `DEBUGGABLE_NATIVE`, so out of the box every wait/sleep wakes ~once per second and re-parks. This is intentional (debuggability) but means a "1 hour sleep" is internally thousands of short sleeps. Install `NATIVE` if you want truly native parking.
- **`isInterrupted()` / `interrupted()` are stateful.** On `NATIVE`, `isInterrupted()` clears the native flag and re-asserts it; `interrupted()` clears it. Neither is a side-effect-free peek. `clearInterrupted()` is implemented by *calling* `interrupted()` for its clearing effect (its return is discarded).
- **`wait(monitor, 0)` does not block.** In `NATIVE`, a non-positive timeout means "don't actually wait" — it just calls `checkInterrupt()` (and `waitNanos` similarly). This differs from `Object.wait(0)`, which waits forever. Pass a positive timeout to actually park.
- **`Condition` dispatch is type-driven.** A `Condition` only gets `WaitService`-aware treatment if it implements `WaitServiceUsingCondition`; otherwise it goes through the `__`-methods, which for `DebuggableWaitService` still apply the periodic cap but otherwise just forward to `raw`.
- **`InheritableThreadLocal`.** Thread-local overrides are inherited by threads spawned after the override is set — convenient, but it means a worker pool created later than expected may or may not inherit, depending on creation order.
- **`get()` re-reads `WaitServiceConfig.current` each call** but caches nothing; cheap, but don't assume a stable instance across calls if another thread is reconfiguring.

## Tech debt / warts

- A commented-out `setThreadLocalDefault_ignorable` / `withThreadLocalDefault_ignorable` pair (and the unused `WaitServiceConfig.current(WaitService notIfThis)` overload they were meant to use) sit dead in the source — an abandoned "don't create the thread-local if it would equal the global default" optimization.
- The huge `Condition` surface (native overload + `WaitServiceUsingCondition` overload + `__`-method + `*Uninterruptibly*` variant, each in several time-unit flavors) is repetitive boilerplate; `Dispatch` and `DebuggableWaitService` re-implement near-identical `instanceof` routing blocks rather than sharing one.
