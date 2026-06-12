# `StackTraceWrapper`

An `Exception` that carries a *pre-captured* stack trace instead of its own, so a remembered call site can be surfaced later in a log.

Source folder: `src`. Package: `pile.utils`.

Up: [utils index](_index.md) · [overview](../../overview.md).

## What it's for

A trivial `Exception` subclass whose only purpose is to act as a **carrier for an externally captured `StackTraceElement[]`**. You hand it a trace (from `Thread.getStackTrace()`), and it presents that trace as if it were its own — so when the exception is logged, the logger prints the captured frames rather than the frames at the throw/construction site.

This is a diagnostics helper: it lets code attach a "this happened here" trace to a warning. It is typically used together with the `debug`-folder flags (see [`DebugEnabled`](../interop/debug/DebugEnabled.md)), whose `DebugEnabled.warn` builds one to give a logged warning a synthetic trace.

## Key methods

- Two constructors — `StackTraceWrapper(String message, StackTraceElement[] stackTrace)` and `StackTraceWrapper(StackTraceElement[] stackTrace)`. Both immediately call `setStackTrace(stackTrace)` to install the supplied trace.
- `fillInStackTrace()` is overridden to a no-op that returns `this` (capturing nothing). This is the crux: it suppresses the JVM's automatic, expensive trace capture at construction time, leaving the trace passed to the constructor as the one that survives.

## Salient behavior

- **The trace is replacement, not a cause.** The wrapper is never chained as a `getCause()` of another exception and is never re-thrown to propagate control. In practice it is constructed and immediately handed to a `Logger` as the `Throwable` argument so the captured frames appear in the log (`DebugEnabled.warn`; `DeadlockDetectingBracket.logStackTrace`, which captures *another* thread's stack to report a stuck bracket).
- **Captures someone else's "now".** Because `fillInStackTrace` is suppressed and the trace is injected, the exception can show frames from a different thread or from an earlier moment — exactly what async/deferred diagnostics need.
- **Order matters internally:** the no-op `fillInStackTrace` ensures the `super(...)` constructor doesn't overwrite the trace; the constructors then `setStackTrace(...)` it. (Even though `setStackTrace` runs after `super`, the override is what makes the captured value the meaningful one and saves the cost of a real capture.)

## Caveats & gotchas

- The class is `public` and extends `Exception`, but it is **not meant to be thrown/caught for control flow** — treat it as a log payload only.
- A `null` or empty `stackTrace` array yields an exception with no frames; there is no validation.
- It does not record *when* or *on which thread* the trace was captured — only the frames. Any such context must go in the `message`.

## Tech debt / warts

- Minimal and self-explanatory; nothing notable. The javadoc ("An Exception that reports a custom stack trace") is accurate but terse.
