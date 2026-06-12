# `pile.aspect.bracket.DeadlockDetectingBracket`

A debug-only decorator that wraps a `ValueBracket` and reports (logs a stack trace by default) when its `open` or `close` runs longer than a timeout — surfacing brackets that are stuck or deadlock-prone.

Source folder: `src`. File: `pile/aspect/bracket/DeadlockDetectingBracket.java`.

`DeadlockDetectingBracket<E, O>` implements [`ValueBracket`](ValueBracket.md) by forwarding every call to a wrapped `back` bracket, but it brackets the forwarded `open`/`close` with a watchdog timer. It exists purely as a diagnostic aid and is meant to be transparent in production. See the package [_index.md](_index.md) and the [overview](../../../overview.md).

## How it detects "stuck" open/close

On `open`/`close` it captures the **calling thread**, schedules a one-shot watchdog on a `ScheduledExecutorService` to fire after `timeoutMillis`, then runs the wrapped operation. In a `finally` it cancels the watchdog. If the wrapped call returns before the timeout, the watchdog is cancelled and nothing is reported; if it hangs (deadlock / slow lock), the watchdog fires and runs `action` against the captured thread. The default `action` is `logStackTrace`, which logs a `WARNING` ("Stuck ValueBracket in thread …") with the stuck thread's current stack trace wrapped in a `StackTraceWrapper`. The detector does **not** interrupt or abort the operation — it only observes and reports.

- Default timeout is **3000 ms**; any `timeout <= 0` is coerced to it.
- Default executor is `StandardExecutors.delayed` when `ses == null`.
- Default action is `this::logStackTrace` when `action == null`.
- Watchdog cancellation asymmetry: `open` cancels with `mayInterruptIfRunning=true`, `close` with `false`. Minor and likely incidental — the watchdog body just calls `action`, so interrupting it has little effect either way.

## Debug-gating (`DETECT_STUCK_BRACKETS`)

This class is not normally instantiated directly. The `ValueBracket` static factories wrap their result in a `DeadlockDetectingBracket` **only when `DebugEnabled.DETECT_STUCK_BRACKETS` is on** (see [`ValueBracket`](ValueBracket.md), Factory catalogue). `DebugEnabled` lives in the `debug` / `debug_off` source folder and its flags are `static final boolean`s used for conditional compilation, so in a production (`debug_off`) build the wrapping is compiled out and this decorator is absent — zero overhead. See the [overview](../../../overview.md) on the `debug`/`debug_off` split.

## The `detectStuck` / `dontDetectStuck` factory hooks

The wrapping is driven through two `ValueBracket` default methods this class overrides:

- `detectStuck` returns `this` — already detecting, so it is idempotent.
- `dontDetectStuck` returns the unwrapped `back` — peeling the decorator off.

On a plain `ValueBracket`, `detectStuck` is what constructs the wrapper and `dontDetectStuck` is the identity; here the two are inverted so opting in/out is cheap and round-trips. There is no public "create" helper on this class itself — construction is via its constructor (called from `ValueBracket`'s `detectStuck` path).

## Metadata forwarding and the no-op fast path

All "shape" methods delegate to `back`: `isInheritable`, `canBecomeObsolete`, and the no-op flags `openIsNop` / `closeIsNop` (captured once into `nopOpen` / `nopClose` in the constructor, ). When the wrapped side is a no-op the watchdog is skipped entirely and the call short-circuits: `open` returns `true`, `close` returns `false` — matching the standard "keep the bracket" / "don't keep the value reference" defaults for a side that does nothing (see [`ValueBracket`](ValueBracket.md) on the asymmetric return booleans).

## `ValueOnly` nested subclass

`DeadlockDetectingBracket.ValueOnly<V>` extends the decorator for a wrapped [`ValueOnlyBracket`](ValueOnlyBracket.md), narrowing `detectStuck`/`dontDetectStuck` to return `ValueOnlyBracket<V>` so the owner-agnostic bracket type is preserved through the wrap/unwrap. `dontDetectStuck` casts `back` to `ValueOnlyBracket<V>` — safe because the constructor is only handed a `ValueOnlyBracket`.

## Caveats & gotchas

- **It reports, it does not fix.** A fired watchdog only logs; the (possibly deadlocked) thread keeps blocking. Use it to find the cause, not to recover.
- **Debug-only.** Present only when `DETECT_STUCK_BRACKETS` is compiled on; don't rely on its behavior in production code paths.
- **Shared timer thread.** The default `StandardExecutors.delayed` executor is shared; the `action` runs on that scheduler thread, not the stuck thread.
- A `timeout` of `0` or negative does not mean "no timeout" — it silently becomes the 3000 ms default.

## Tech debt / warts

- The `open`/`close` watchdog-cancellation `mayInterruptIfRunning` differs (`true` vs `false`) with no apparent reason; harmless but inconsistent.
- No way to disable the timeout entirely (non-positive coerces to 3 s).

## See also

- [`ValueBracket`](ValueBracket.md) — the interface; its factories install this wrapper under `DETECT_STUCK_BRACKETS` and expose `detectStuck`/`dontDetectStuck`.
- [`ValueOnlyBracket`](ValueOnlyBracket.md) — wrapped by the `ValueOnly` nested subclass.
- package [_index.md](_index.md) · [overview](../../../overview.md) · [concepts](../../../concepts/).
