# `DebugEnabled`

The `static final boolean` debug flags exploited for conditional compilation, so debug code is stripped by the compiler when a flag is `false`.

> **Source folder: `debug`** (NOT `src`). The file is `debug/pile/interop/debug/DebugEnabled.java`. There is a swappable twin at `debug_off/pile/interop/debug/DebugEnabled.java` whose flags are all compiled to `false`. **Only one of `debug` / `debug_off` is on the build path at a time** — the project `.classpath` currently uses `debug`. Toggle debugging by swapping the two source folders on the build path (or by editing the flags), then **recompiling** the library. See [overview](../../../overview.md) and the [`interop` index](../_index.md).

Up: [`interop` index](../_index.md) · [overview](../../../overview.md).

## What it's for

`DebugEnabled` centralises the decision of *what runtime effort Pile spends tracking debugging-relevant data*. Each flag is a `public static final boolean`. Because they are compile-time constants, `if (DebugEnabled.SOME_FLAG) { …expensive debug code… }` blocks are **eliminated by the Java compiler** when the flag is `false` — the debug code carries zero runtime cost in a non-debug build and the JIT never sees it. This is **conditional compilation**: the only way to change a flag's effect is to edit it (or swap in the `debug_off` twin) and **recompile** the library. The flags are referenced pervasively across Pile (`PileImpl`, `AbstractReadListenDependency`, the brackets, recomputation machinery, …).

The class also holds a few small helper methods and shared debug-only state (a trace set, a per-thread bracket-lock counter, a per-thread "stop requested" registry).

## The flags

All flags are gated on the master kill-switch `DISABLE_ALL_DEBUGGING` (a `static final boolean`, currently `false`). Each flag is written `!DISABLE_ALL_DEBUGGING && <local toggle>` (or, for `ET_TRACE`, gated on `DE`), so flipping `DISABLE_ALL_DEBUGGING` to `true` forces every flag to `false` in one edit — useful for a guaranteed-clean release build.

### Master switch

- **`DISABLE_ALL_DEBUGGING`** — global off-switch; when `true`, all the flags below collapse to `false`.
- **`DE`** ("debug enabled") — the master debugging flag. Defined `!DISABLE_ALL_DEBUGGING && false`. When `false`, [`DebugCallback`](../../impl/DebugCallback.md)s are not invoked. `ET_TRACE` is gated on `DE` (it can only be on if `DE` is on).

### Tracing (per-value decision traces)

- **`ET_TRACE`** — defined `DE && true`; save detailed traces in every `AbstractReadListenDependency` for which `traceEnabledFor` returns `true`. A trace records the decisions made since the last `endTransaction`, with stack traces for where each happened. **Very slow** — see the field javadoc. Only effective when `DE` is also on.
- **`trace`** — a `Collections.synchronizedSet` of objects (reactive values) for which a detailed trace is saved. Only has an effect when `ET_TRACE` is `true`. Membership in this set is the default criterion consulted by `traceEnabledFor`.
- **`TRANSACTION_TRACES`** — defined `!DISABLE_ALL_DEBUGGING && false`; enables transaction-level traces. See [concepts/transactions.md](../../../concepts/transactions.md) for what a transaction is.
- **`DEPTH_WARNING`** — defined `!DISABLE_ALL_DEBUGGING && false`; warn about (recursion/recompute) depth.

### Stuck-bracket / deadlock detection

- **`DETECT_STUCK_BRACKETS`** — defined `!DISABLE_ALL_DEBUGGING && true`; wraps every `ValueBracket` created by the library's static methods into a `DeadlockDetectingBracket`.
- **`COUNT_BRACKET_LOCKS`** — defined `!DISABLE_ALL_DEBUGGING && false`; count the value-mutices a thread holds because of opening/closing `ValueBracket`s (via the `ThreadLocal` counter `lockedValueMutices`), so an operation that runs while such a mutex is held can warn. Brackets normally run while the value's internal mutex is held and can execute arbitrary user code (e.g. `destroy`), which risks deadlock.
- **`WARN_ON_DESTROY_WHILE_LOCKED`** — defined `!DISABLE_ALL_DEBUGGING && true`; log a warning if a `PileImpl` or `Independent` is destroyed while value mutices are locked in the same thread.

### Recomputation guards

- **`RENAME_RECOMPUTATION_THREADS`** — defined `!DISABLE_ALL_DEBUGGING && !false` (i.e. `true`); rename threads for the duration of a `Recomputation`, so stack dumps / profilers show which value a thread is recomputing.
- **`ERROR_ON_CREATE_IN_DYNAMIC_RECOMPUTATION`** — defined `!DISABLE_ALL_DEBUGGING && true`; throw an `IllegalStateException` from a reactive value's constructor if it runs during a dependency-recording `Recomputation` (catches accidentally creating values inside a dynamic recompute, which would mis-record dependencies).

## Shared debug-only state

- **`lockedValueMutices`** — `ThreadLocal<MutInt>`; per-thread counter of mutices held due to bracket open/close. Initialised to a counting `ThreadLocal` only when `COUNT_BRACKET_LOCKS` is `true`, otherwise **`null`** (callers must guard accesses with the flag — see gotchas).
- **`trace`** — see Tracing above.
- **`STOP_REQUESTED`** — private `WeakHashMap<Thread, Object>` backing the stop-request helpers below; weak keys so dead threads drop out.

## Helper methods

- **`traceEnabledFor(Object d)`** — whether `d` should save a detailed trace. Default = membership in `trace`; the javadoc invites you to extend it with other criteria. Consulted by the implementations (e.g. in `AbstractReadListenDependency`) only when `ET_TRACE` is on.
- **`warn(Logger log, String string)`** — log a warning at `Level.WARNING` with a synthetic `StackTraceWrapper` exception capturing the current stack (so you get a stack trace without throwing). If `log` is `null`, prints the trace to stderr instead.
- **`printStackTrace(...)`** (several overloads) — print a synthetic stack trace (it throws-and-catches an `Exception` internally) to a `PrintStream`, optionally with a custom message. Defaults to `System.err` and message `"trace"`.
- **Stop-request registry** — a cooperative "ask this thread to stop" mechanism backed by `STOP_REQUESTED`:
  - `requestStop(Thread t, Object o)` / `requestStop(Thread t)` — register a stop request (with an optional message payload).
  - `isStopRequested(Thread t)` — query.
  - `clearStopRequested(Thread t)` — remove and return the payload.
  - `stopIfRequested(Thread t)` / `stopIfRequested()` — if a stop is pending for the thread, clear it and `System.out.println` the message payload.

## Caveats & gotchas

- **Changing a flag requires recompiling Pile.** These are compile-time constants by design; there is no runtime switch. For a release, the cleanest path is to put `debug_off` on the build path (all flags `false`) or set `DISABLE_ALL_DEBUGGING = true`.
- **`debug` vs `debug_off` are twins, not two files compiled together.** Exactly one is on the build path. When reading flag *values* in the active build, read the one named in `.classpath` (`debug`).
- **`lockedValueMutices` is `null` unless `COUNT_BRACKET_LOCKS` is on.** Any access must be guarded by the flag, or it NPEs. This is intentional (the field is only allocated when the feature is compiled in).
- **Flag values are interdependent.** `ET_TRACE` can never be `true` while `DE` is `false`; every flag is `false` while `DISABLE_ALL_DEBUGGING` is `true`. Don't read a local toggle (`… && true`) in isolation.
- **`stopIfRequested` is cooperative**, not preemptive — it only does anything if the target thread actually calls it; it prints the message but does not itself interrupt or stop the thread.

## Tech debt / warts

- The mixed gating idioms (`!true`, `&& true`, `&& false`, `&& !false`) are a hand-toggle convention — a developer flips the trailing literal to switch a feature. Readable once you know the pattern, but easy to misread (`!false` = `true`).
- `printStackTrace` deliberately throws and catches an `Exception` purely to capture a stack; `warn` uses a `StackTraceWrapper` for the same purpose — two slightly different mechanisms for "give me a stack trace here."
- `stopIfRequested` routes its message to `System.out` while `warn`/`printStackTrace` default to `System.err` — inconsistent debug output sinks.

## See also

- [`DebugCallback`](../../impl/DebugCallback.md) — the per-value debug hook that `DE` gates.
- [concepts/transactions.md](../../../concepts/transactions.md) — what `TRANSACTION_TRACES` and the per-value decision traces are recording.
- [`interop` index](../_index.md) · [overview](../../../overview.md).
