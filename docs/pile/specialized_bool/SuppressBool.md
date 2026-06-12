# `pile.specialized_bool.SuppressBool`

A reference-counted reactive suppression flag: `false` at rest, `true` while any `Suppressor`s produced by `suppress()` remain outstanding.

Source folder: `src`. File: `pile/specialized_bool/SuppressBool.java`.

Up: [bool index](_index.md) · [overview](../../overview.md). Extends: [`IndependentBool.md`](IndependentBool.md). Suppressor model: [`../aspect/suppress/_index.md`](../aspect/suppress/_index.md).

## What it is

`SuppressBool` extends `IndependentBool` and **seals itself at construction** (calls `seal()` in the constructor). Its initial value is `false`. The only legitimate way to change its value is via `Suppressor`s from `suppress()`: each outstanding suppressor holds the count above zero, flipping the value to `true`; when the last suppressor is released the count returns to zero and the value reverts to `false`.

It is the boolean dual of the `AutoValidationSuppressible` / `SuppressInt` pattern — a reference-counted flag value used to signal "something is currently suppressed / in progress."

The static field `METHOD = SuppressBool::suppress` lets it be passed as a `Function` to generic suppression machinery that expects a factory producing `Suppressor`s from a `SuppressBool`.

## Construction and lifecycle

```
SuppressBool sb = new SuppressBool();         // false, sealed
sb.asyncChange();                              // optional: changes routed via own SequentialQueue
sb.asyncChange(existingQueue);                 // optional: share an existing queue
sb.asyncRelease(executorService);              // optional: release on a thread pool
```

After construction it cannot be renamed or further configured structurally (it is sealed). Configure async options before use.

## The counter and value transitions

An internal `int suppressors` field counts outstanding suppressors. The value presented to observers is `suppressors > 0`. Both increment and decrement are done inside `synchronized(setter)` to guard the counter + the value write as one atomic action.

### `suppress()` — acquire a suppressor

1. Creates a `Suppressor` wrapping `__decrement`.
2. Calls `__increment(suppressor)` — increments the counter and writes `true` if the count crossed from 0 to 1.
3. If `asyncRelease` is configured, wraps the suppressor via `Suppressor.wrapAsync(asyncRelease)` so its release will run on that executor.
4. Returns the suppressor.

### `__increment` / `__decrement` — the counter mechanics

- **Without `asyncChange`:** both run synchronously in the caller's thread, holding `synchronized(setter)` while writing to the `IndependentBool`.
- **With `asyncChange` (`SequentialQueue`):** the counter mutation is split: the calling thread increments/decrements while holding the lock (so the logical change is immediate), and a task is enqueued on `asyncChange` that decrements the counter again and writes the new value. This double-decrement pattern means the actual value write is deferred to the queue thread while keeping the counter consistent. The enqueue in `__increment` uses `syncEnqueue` (blocks until the task is accepted); `__decrement` uses plain `enqueue` (fire-and-forget).

**Caution (async mode):** `__increment` catches `InterruptedException` from `syncEnqueue`, logs a warning, and re-interrupts the thread. If the enqueue is interrupted, the suppressor created just before is released in the `finally` block to avoid a stuck-open suppression.

## `suppressBracket(inheritable, predicate)` — suppress while a value matches

Returns a `ValueBracket<T, Object>` that increments the counter when a monitored value satisfies `predicate` and decrements it when the value no longer does (or the bracket closes). The bracket:

- Tracks how many monitored objects currently have it open (via a `MutInt openCount`).
- Is registered with `WeakCleanup`: if the bracket object becomes weakly reachable before it is fully closed, the cleanup runner decrements the counter for each still-open application and logs a warning. This is the GC-safety net; prefer explicit close in normal usage.
- Honors `DebugEnabled.DETECT_STUCK_BRACKETS` — the returned bracket is wrapped with stuck-bracket detection if that flag is on.

The predicate **must be stable** (same object must always map to the same result), as the bracket tests the predicate both on open and close but has no snapshot storage of which objects passed.

## Direct-write methods — deprecated and failing

`set(Boolean)`, `setTrue()`, `setFalse()`, and `setNull()` are `@Deprecated` and their javadoc says "will fail." Because the value is sealed, calling them routes through the seal interceptor, which (in the default throw mode) throws `IllegalStateException`. This is intentional, not a bug — the value is solely counter-managed.

## Caveats & gotchas

- **Always call `suppress()` and release the returned `Suppressor`.** Dropping a suppressor without releasing leaks a permanent `true` state. Use try-with-resources or store the suppressor for manual release.
- **The async-change double-decrement** means the observable value update can lag behind the logical counter change when `asyncChange` is configured. During the queue drain window the value may be stale.
- **`suppressBracket` predicate must be referentially stable.** If the same object can change its predicate result over time, open/close counts will diverge.
- **Sealed at construction** — no structural configuration after `new SuppressBool()`. Set `asyncChange`/`asyncRelease` before first use; they are plain field writes (not guarded by the seal because they are not `IndependentBool`/`Independent` structural APIs).
- **`set`/`setTrue`/`setFalse`/`setNull` throw at runtime.** Do not call them. If your code holds a generic `IndependentBool` reference and calls `set`, it will blow up if the object is a `SuppressBool`.

## Common tasks

- **Signal "something is suppressed" reactively:**
  `SuppressBool suppressed = new SuppressBool();`
  `Suppressor s = suppressed.suppress();`
  — `suppressed` is `true` while `s` is outstanding.
- **Suppress while a value matches a condition:**
  `ValueBracket<Foo, Object> b = suppressed.suppressBracket(false, Foo::isBusy);`
  Apply `b` to reactive values via their bracket API.
- **Route counter writes to a dedicated thread:**
  `suppressed.asyncChange();` or `suppressed.asyncChange(myQueue);`
- **Release a suppressor on a thread pool:**
  `suppressed.asyncRelease(myExecutor);`

## Tech debt / warts

- **Commented-out logging in `logIncrement` / `logDecrement`** — dead code left from debugging; the methods are protected so subclasses can re-enable them, but the intent is unclear.
- **`asyncChange` TODO** in `__increment`: "is this really exception safe?" — the exception safety of `syncEnqueue` under `InterruptedException` is handled by the `finally` block, but the author noted uncertainty.
- **The `asyncChange` double-decrement** is subtle and could surprise readers: the counter changes twice per call (once in the calling thread, once in the queue), so tracing the counter in a debugger without understanding the pattern looks like a bug.
- The javadoc on `asyncChange(SequentialQueue)` references `SuppressInt` instead of `SuppressBool` (copy-paste).

## Related

- [`IndependentBool.md`](IndependentBool.md) — the base class; `SuppressBool` inherits all its reactive behavior.
- [`../aspect/suppress/_index.md`](../aspect/suppress/_index.md) — the `Suppressor` type and the full suppress/release model.
- [`_index.md`](_index.md) — family overview; `SuppressBool` and `MutBool` are the two non-generic-impl bool types.
- [`combinations/_index.md`](combinations/_index.md) — `ReadWriteListenDependencyBool`.
