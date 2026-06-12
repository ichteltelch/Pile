# `WaitServiceUsingCondition`

A `Condition` whose every blocking/signalling operation is routed through a `WaitService`, so that awaiting on it obeys the host's injected wait/notify semantics instead of native ones.

Source folder: `src`. Package `pile.interop.wait`.

Up: [wait index](_index.md) · [interop overview](../../../overview.md).

## What it is (and what it is *not*)

Despite the task framing, `WaitServiceUsingCondition` is **not** a `WaitService` implementation — it is the **mirror image**: a subinterface of `java.util.concurrent.locks.Condition` (`WaitServiceUsingCondition extends Condition`) whose awaiting is *performed by* a `WaitService`. Where [`WaitService`](WaitService.md) abstracts `wait`/`notify`/`sleep`/`interrupt` for arbitrary monitors, this interface is the bridge that lets a `Condition` participate in that same injectable scheme.

The two cooperate as a closed loop:
- A `WaitServiceUsingCondition` delegates each `Condition` method to its `WaitService`-aware twin (see below), passing the *current* `WaitService` (`WaitService.get()`).
- A `WaitService` that receives an ordinary `Condition` (in `WaitService.Dispatch`/`DebuggableWaitService`) checks `c instanceof WaitServiceUsingCondition` and, if so, calls *back* into the condition's `ws`-aware methods (the `WaitService.await(WaitServiceUsingCondition c)` default family). So control bounces condition → service → condition, with the service deciding the actual timing policy (e.g. periodic wakeups).

Implemented in `src` by the sibling conditions [`NativeCondition`](NativeCondition.md) and [`WrappedCondition`](WrappedCondition.md).

## The two-method-per-operation pattern

For each `Condition` method there are **two** declarations:

- a `default` override of the `Condition` method that simply calls the `ws`-parametrised twin with `WaitService.get()` — e.g. `await()` → `await(WaitService.get())`, `signal()` → `signal(WaitService.get())`. These defaults are fixed; an implementor does **not** override them.
- an **abstract** `ws`-first overload that the implementor must supply: `await(WaitService ws)`, `awaitNanos(WaitService ws, long)`, `await(WaitService ws, long, TimeUnit)`, `awaitUntil(WaitService ws, Date)`, `awaitUninterruptibly(WaitService ws)`, `signal(WaitService ws)`, `signalAll(WaitService ws)`.

So the real contract an implementor fills is *"do this Condition operation, but block via this `WaitService`."* The thread-local indirection (`WaitService.get()`) is handled once, here, for every implementor.

### Extra (non-`Condition`) operations

Three methods have no `Condition` counterpart and exist only in the `ws`-aware form:
- `awaitNanosUninterruptibly(WaitService ws, long nanos)` — abstract.
- `awaitUninterruptiblyUntil(WaitService ws, Date deadline)` — abstract.
- `awaitUninterruptibly(WaitService ws, long time, TimeUnit unit)` — the only `default` twin here: it forwards to `awaitNanosUninterruptibly(ws, unit.toNanos(time))` and reports success as `> 0` (interpreting a positive returned remaining-nanos as "did not time out"). Mirrors `WaitService.awaitUninterruptibly(Condition, long, TimeUnit)`.

These uninterruptible variants are what `WaitService` (and `DebuggableWaitService`) lean on to loop with bounded waits while preserving an interrupt for re-delivery on exit.

## What host behaviour this enables

Because every await goes through the injected `WaitService`, a `WaitServiceUsingCondition` automatically gains whatever the active service provides — without the condition implementation knowing about it. In particular, under `WaitService.DEBUGGABLE_NATIVE` / `DebuggableWaitService`, a thread parked on such a condition is woken **periodically** (default 1000 ms) rather than parked indefinitely, so a debugger can inspect it; the `Date`/`nanos` deadline math in the `__await*` helpers of `DebuggableWaitService` reconstructs the true remaining timeout across those wakeups. Equivalently, a service that repurposes interruption as a signalling channel changes what "interrupted while awaiting" means for the condition, uniformly.

## Caveats & gotchas

- **Naming is inverted from intuition.** "UsingCondition" reads as if it were a service built *on* conditions; it is the opposite — a condition built to *use* a service. Keep the direction straight: condition delegates to service.
- **`WaitService.get()` is read per call** (thread-local, falling back to the global default in `WaitServiceConfig`). The service in force is whichever is active *when the no-arg `Condition` method is invoked*, not when the condition was created. Switching the thread-local default (e.g. via `WaitService.withThreadLocalDefault`) mid-await does not retro-actively change an in-progress wait, only subsequent calls.
- **Do not override the `default` `Condition` methods** in an implementor; override only the `ws`-aware overloads. Overriding the no-arg forms would break the `get()` indirection.
- The `boolean` returned by the timed uninterruptible default (`awaitUninterruptibly(ws, time, unit)`) is "still had time left" (`remaining > 0`), inherited from the `awaitNanos*` convention of returning the estimated remaining nanos; treat it as "did not time out," not as "predicate satisfied."

## Common tasks

- **Make a new awaitable usable under injected waiting:** implement `WaitServiceUsingCondition` (rather than raw `Condition`) and fill the seven abstract `ws`-aware methods plus `awaitNanosUninterruptibly`/`awaitUninterruptiblyUntil`; route their actual blocking through the passed `ws` (typically `ws.wait(monitor, ...)` / `ws.notifyAll(monitor)` on an internal monitor). See [`NativeCondition`](NativeCondition.md) for the straightforward backing-monitor version and [`WrappedCondition`](WrappedCondition.md) for decoration.
- **Await with a specific service explicitly:** call the `ws`-aware overload directly (`cond.await(myWs)`), bypassing `WaitService.get()`.

## Tech debt / warts

- The interface name actively misleads about the dependency direction (see caveats).
- The boolean-from-remaining-nanos convention (`> 0`) is shared with `WaitService` but is subtle; it conflates "time remained" with "success" and is easy to misread.
