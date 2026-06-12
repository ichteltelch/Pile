# `Suppressor`

A reified "release-to-undo" handle: creating it starts suppressing some behavior; the first `release` ends it.

Source folder: `src` — package `pile.aspect.suppress` (one level below `pile/aspect/`).

`Suppressor` is the value returned by every suppress/transaction method in the aspect layer: [`DoesTransactions.transaction`](../DoesTransactions.md), [`AutoValidationSuppressible.suppressAutoValidation`](../AutoValidationSuppressible.md), [`Dependency`](../Dependency.md) deep-revalidation suppression, [`RemembersLastValue.suppressRememberLastValue`](../RemembersLastValue.md), and similar. You hold the returned `Suppressor`, do your work, and `release` it (typically via try-with-resources, since it `extends SafeCloseable`) to undo the suppression.

Up: package [`_index.md`](../_index.md) · overview [`../../../overview.md`](../../../overview.md) · concept [`../../../concepts/transactions.md`](../../../concepts/transactions.md).

## What it's for

The suppression is **active from construction until the first `release`**. The behavior stays suppressed as long as *at least one* un-released `Suppressor` for it exists — these are typically reference-counted by the suppressed object, so multiple overlapping suppressions nest correctly. It is a `Runnable` too (`run` just calls `release`), so it drops into APIs that take a cleanup `Runnable`.

## Key members by purpose

- **`release`** — stop suppressing, as far as *this* instance is concerned. Returns `true` only on the first call.
- **`close`** — try-with-resources hook; just calls `release`.
- **`run`** — `Runnable` hook; calls `release`. Javadoc says **do not override** it — several methods (`wrapAsync`) assume the default.
- **`isDefinitelyReleased`** — `true` only if surely released; `false` may mean "not released" *or* "uncertain". It is a conservative one-way signal.
- **`NOP`** — shared no-op `Suppressor` (suppresses nothing, `release` returns `false`, `isDefinitelyReleased` returns `true`). Use instead of `null` to avoid null-checks.
- **`wrapWeak` / `wrapWeak(String warn)`** — GC-safety net (see below).
- **`many(...)`** — build a `SuppressMany` that releases several children at once; many overloads (collection / iterable / varargs / `Function`-per-element / `BiConsumer`-per-element).
- **`wrap(Runnable)`** — adapt an arbitrary `Runnable` into a run-at-most-once `Suppressor`; **`wrapWeak(Runnable)`** = `wrap` + `wrapWeak`.
- **`replace(...)`** — create the next suppressor (weak-wrapped) and release *this* one before returning; for swapping one suppression for another atomically.
- **`cancellableRelease`** — returns a `CancelClose` so a try-with-resources block's auto-release can be *cancelled* and the responsibility handed to another thread/listener (; see `CancellableRelease`).
- **`wrapAsync([ExecutorService])`** — release happens on an executor; on `RejectedExecutionException` it falls back to releasing synchronously and logs SEVERE.
- **`lift` / `liftArray`** — lift a per-element suppression method to one over `Iterable`s / arrays.

## Salient / caveats

### `release` is idempotent
Calling `release` more than once is safe; only the first call does work and returns `true`, subsequent calls return `false` and do nothing. This is what makes try-with-resources plus a manual hand-off (e.g. via `cancellableRelease`) safe. `Wrapped.release` and `SuppressMany.release` null out their payload under `synchronized` so the run-once guarantee holds across threads.

### GC-tied release: `wrapWeak` vs. the bare suppressor
A bare `Suppressor` does **not** release itself when garbage-collected — the suppression simply persists forever (a leak), because the suppressed object keeps it ref-counted, not strong-referenced. **`wrapWeak` is the fix**: it returns a `WrapWeak` whose `release` is invoked once the wrapper becomes weakly reachable (via `WeakCleanup.runIfWeak`, ). So the rule of thumb:

- Keep the returned `Suppressor` **strongly reachable** for exactly as long as you want the suppression — i.e. release it explicitly.
- If you instead want the suppression to last "until this owner is collected", `wrapWeak` it and let the owner hold it.

If wrapper construction throws, the underlying suppressor is released immediately so it can't leak.

### The "you forgot to release" warning
`wrapWeak(String warn)` (non-null `warn`) logs `WARNING "You forgot to release a Suppressor (hint: <warn>)"` **if and only if** the wrapper became weakly reachable while the underlying suppressor was still un-released. This is the documented gotcha: dropping the last strong reference to a `WrapWeak`-with-warning without releasing it surfaces a logged warning (logger names `"Suppressor"` / `"Suppressor.WrapWeak"`). The plain `wrapWeak` (no warn) releases silently. `WrapWeak.TRACE` is `true`, so each `WrapWeak` captures a construction stack trace and its `toString` prints it — handy for tracking down the leak site.

### `NOP` and `WrapWeak.release` rebinding
`NOP` is a singleton you can pass anywhere a `Suppressor` is wanted with zero effect. Note `WrapWeak.release` sets its backing field to `NOP` after releasing, so the wrapped suppressor becomes eligible for GC and a stale double-release is harmless.

### `SuppressMany` is not thread-safe after release / `add` ordering
`SuppressMany.add(...)` throws `IllegalStateException` if called after `release`. The `more(...)` overloads differ from `add(...)`: if anything is thrown while adding, they call `release` on the partially-built group — use `more` when adding can fail and you want all-or-nothing cleanup. `makePlaceFor1` pre-grows capacity so a subsequent `add` can't `OutOfMemoryError` mid-operation.

## Common tasks

- **Suppress for a scope:** `try (Suppressor s = v.suppressAutoValidation) { ... }` — auto-released at block end.
- **Suppress until owner is GC'd:** store `v.suppressAutoValidation.wrapWeak("my-owner")` in the owner; the warning hint helps if you forget.
- **Suppress several at once:** `Suppressor.many(Value::suppressAutoValidation, listOfValues)` → one handle whose `release` releases all.
- **Release on another thread:** `s.wrapAsync` (standard executor) or `s.wrapAsync(exec)`.
- **Hand off release out of a try-block:** `cancellableRelease` + `CancelClose.release` (see the worked example at ).

## See also (siblings in `pile.aspect.suppress`, docs pending)
`CancelClose` / `CancellableRelease` (cancel an auto-release), `SafeCloseable` (the `close`-without-checked-exceptions super-interface), `MockBlock`, `SuppressionSwitcher` / `ReactiveSuppressionSwitcher`. A future `suppress/_index.md` should gather these.
