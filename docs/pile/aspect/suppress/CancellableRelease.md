# `CancellableRelease`

A one-shot release action for a [`Suppressor`](Suppressor.md) that can be cancelled, so a try-with-resources block's auto-release becomes a no-op once the responsibility has been handed off.

Source folder: `src` — package `pile.aspect.suppress`.

It is the concrete `final` implementation of [`CancelClose`](CancelClose.md) returned by [`Suppressor.cancellableRelease`](Suppressor.md). Up: package [`_index.md`](_index.md) · overview [`../../../overview.md`](../../../overview.md) · concept [`../../../concepts/`](../../../concepts/).

## What it's for

You usually `release` a `Suppressor` at the end of a `try (...)` block. Sometimes you instead want to *keep the suppression alive past the block* — handing the release duty to another thread, a listener, or a later callback. `cancellableRelease` gives you a `CancelClose` to put in the try-with-resources slot, plus a `cancel` you call inside the block when the hand-off succeeds. Cancelling makes the auto-`close` do nothing, so the suppression survives. See the worked example referenced from `Suppressor.java`.

## How it differs from a plain release

- A plain `Suppressor.release` (or its try-with-resources `close`) *always* runs. `CancellableRelease.close` runs the underlying `release` **only if `cancel` was not called first**.
- It wraps an existing `Suppressor` (`s`); it owns no suppression state of its own — it is purely a guard over *whether* that suppressor's `release` fires.

## Cancel semantics

- `cancel` simply nulls the stored `Suppressor` reference. After that, `close` sees `s == null` and does nothing — the suppression is left in place for whoever now owns it.
- `close` is `@Deprecated` (per `CancelClose`, "Should only be called by a try-with-resources block"). Don't call it by hand; let the `try` block invoke it.
- The class is **not thread-safe** in itself (plain field, no synchronization). That is fine for the intended pattern: `cancel` runs inside the owning thread's try-block before the block exits, and the real run-once safety lives in the underlying `Suppressor.release`, which nulls its payload under `synchronized` and is idempotent (see [`Suppressor.md`](Suppressor.md) § `release` is idempotent).

## Who produces it

Only [`Suppressor.cancellableRelease`](Suppressor.md). There is no other constructor caller in the framework; treat it as an implementation detail of that method and program against the [`CancelClose`](CancelClose.md) interface.

## Common task

Hand off a suppression out of a try-block:

```
try (CancelClose c = suppressor.cancellableRelease) {
    if (handedOffTo(otherThread)) c.cancel; // otherThread now owns release
    // else: c.close at block end releases as usual
}
```

## Caveats & gotchas

- **Cancelling does not release** — it transfers the obligation. If nobody you handed it to ever calls the underlying `release`, the suppression leaks. If "until the owner is GC'd" is acceptable, prefer `Suppressor.wrapWeak(...)` instead (see [`Suppressor.md`](Suppressor.md) § GC-tied release).
- No `NOP`-style guard against double-close needed here: a second `close` after a real release just re-invokes the (idempotent) `Suppressor.release`, which returns `false` and does nothing.

## Tech debt / warts

- `s` is a package-private mutable field with no `final` and no synchronization; correctness relies entirely on the single-threaded try/cancel usage pattern and on `Suppressor.release` being idempotent. Robust only within that idiom.
