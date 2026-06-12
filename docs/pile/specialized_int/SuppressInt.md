# `SuppressInt` — reference-counted suppression counter; int dual of `SuppressBool`

`SuppressInt` is a self-managed integer whose value tracks the count of currently outstanding `Suppressor` tokens. It extends `IndependentInt`, seals itself at construction, and exposes `suppress()` to acquire a token; the value is always the live suppressor count (0 = nothing suppressed). It is the int analogue of `SuppressBool` (see [`../specialized_bool/_index.md`](../specialized_bool/_index.md) § SuppressBool notes).

Source folder: `src`. Package: `pile.specialized_int`.

Up: [int index](_index.md) · [overview](../../overview.md). Generic base: [`IndependentInt.md`](IndependentInt.md). Bool analogue: see the bool family index § SuppressBool.

## Class hierarchy & invariants

`SuppressInt extends IndependentInt` — it is an always-valid reactive integer, initialized to `0` and sealed immediately in the constructor. Direct writes (`set(v)`) are `@Deprecated` on the `SuppressBool` pattern; the value is driven entirely by the internal `suppressors` counter via a `Consumer<Integer> setter`.

## How suppression works

Call `suppress()` to obtain a `Suppressor` token; this atomically increments the internal `suppressors` counter and propagates the new count into the reactive value. When the `Suppressor` is released (via `Suppressor.release()` or try-with-resources), the counter is decremented and the value updated. The live count is always the current reactive value; downstream reactive nodes observing this `SuppressInt` see the actual reference count, not just a boolean flag.

`METHOD` is a static `Function<SuppressInt, Suppressor>` alias for `SuppressInt::suppress`, useful for passing the factory as a method reference.

## Async configuration

Two fluent builder methods control threading of the counter updates:

- `asyncChange()` / `asyncChange(SequentialQueue)` — if set, all increments and decrements enqueue the reactive update onto the given `SequentialQueue` (a new one is created by the no-arg form). The counter itself is still modified synchronously under `synchronized(setter)` to maintain accurate counting; only the `setter.accept(count)` call is deferred. This avoids holding a lock while notifying listeners.
- `asyncRelease(ExecutorService)` — if set, `Suppressor`s returned by `suppress()` are wrapped via `Suppressor.wrapAsync(executor)` so their release happens in the given executor rather than in the caller's thread.

When `asyncChange` is null (the default), the `setter.accept` call is made synchronously while holding `synchronized(setter)`.

## `suppressBracket` — predicate-driven suppression

`suppressBracket(boolean inheritable, Predicate<T> crit)` returns a `ValueBracket<T, Object>` that suppresses the `SuppressInt` as long as the bracketed value matches `crit`. The bracket can be attached to multiple values; it uses a `MutInt openCount` to track how many values it is currently open on. If the bracket becomes weakly reachable before being fully closed, a `WeakCleanup` finalizer fires `decrement()` for each unclosed open — preventing a permanently elevated count. If `DebugEnabled.DETECT_STUCK_BRACKETS` is set, the bracket is wrapped with stuck-detection.

Caution: the javadoc for `suppressBracket` states the `crit` predicate **must always evaluate to the same result for the same object** — it is evaluated both on `open` and `close` and must be consistent between the two calls.

## `idemGuard` — idempotent execution guard

`idemGuard(int limit, Consumer<Runnable> exa, Runnable job)` provides an idempotency guard: it increments the suppression count, registers a callback via `exa` (typically an event executor), and only runs `job` when the callback fires if the live count is still below `limit`. This is used to debounce or rate-limit reactive jobs: a second trigger while the first is pending will see `get() >= limit` and skip. The two-arg overload adds an `onDiscard` callback for the skipped case.

## Caveats & gotchas

- **Direct writes deprecated** — setting the value directly bypasses the counter and is not supported; the value should only change through `suppress()`/`release()` cycles.
- **`asyncChange` + interruption** — if `asyncChange.syncEnqueue` is interrupted, the `increment` call catches `InterruptedException`, re-interrupts the thread, and attempts to release the suppressor in the `finally` block to avoid a leaked increment. However, the TODO comment in the source (`"is this really exception safe?"`) acknowledges this path may not be fully safe under all failure modes.
- **`suppressBracket` predicate consistency** — the predicate is called on both `open` and `close`; if it returns a different result for the same object between calls, the open/close counts will diverge and the suppressor count will be wrong.
- **Weak-cleanup finalizer** — if a `suppressBracket` is garbage-collected while still open, the finalizer logs `"suppressBracket leaked!"` (and a secondary warning if not all decrements succeeded). The GC-based safety net is best-effort; rely on explicit bracket lifecycle management.
- **Count is the value, not a boolean** — unlike `SuppressBool`, downstream observers see the raw count. Use `isZero()` / `isNonZero()` (via `PileInt`) to derive a boolean suppression flag if needed.

## Tech debt / warts

- The `TODO` comment on `asyncChange.syncEnqueue(...)` in `increment` acknowledges uncertain exception safety under interruption.
- The `WeakCleanup` finalizer logs `"leaked suppressBracket no completely closed!"` — the message has a grammatical error (`no` instead of `not`).
