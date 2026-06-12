# `CancelClose`

A try-with-resources closeable handle whose pending `close` can be neutralized by calling `cancel` first — close-on-scope-exit that you can opt out of.

Source folder: `src` — package `pile.aspect.suppress`.

`CancelClose` extends [`SafeCloseable`](SafeCloseable.md), so it is an `AutoCloseable` with no checked exception on `close` and slots into a try-with-resources block. Its extra method, `cancel`, **disarms** the handle: once `cancel` has been called, the eventual `close` does nothing. This lets a try-with-resources block *conditionally* hand off the responsibility it would otherwise discharge at block end — you cancel inside the block so the auto-`close` becomes a no-op, and someone else (another thread, a listener) becomes responsible for the real cleanup.

Up: package [`_index.md`](_index.md) · overview [`../../../overview.md`](../../../overview.md).

## What it's for

The defining contract: **calling `cancel` makes the subsequent `close` a no-op.** It is *not* a handle whose `close` cancels something else; the name reads as "a close you can cancel". The canonical producer is [`Suppressor.cancellableRelease`](Suppressor.md), which wraps a `Suppressor` so that its auto-release at the end of a try-with-resources block can be cancelled and the release responsibility handed off — see the worked example referenced from the [`Suppressor`](Suppressor.md) doc. The concrete implementation lives in the sibling [`CancellableRelease`](CancellableRelease.md).

## Key members by purpose

- **`cancel`** — disarm the handle. After this call, `close` must do nothing. Idempotency/threading guarantees depend on the implementation (see [`CancellableRelease`](CancellableRelease.md)); the interface only states the no-op-after-cancel contract.
- **`close`** — the try-with-resources hook (overrides `SafeCloseable.close`). **`@Deprecated`** with the note "Should only be called by a try-with-resources block" — i.e. you are not meant to call `close` by hand; let the language construct invoke it.
- **`NOP`** — a shared do-nothing instance whose `close` and `cancel` are both empty. Use it instead of `null` for an "already inert" handle to avoid null checks.

## Salient / surprising behavior

- **`close` is deprecated on purpose, not because it's going away.** The `@Deprecated` is a *lint nudge*: it makes manual `handle.close` calls show up as warnings, steering you toward `try (...)`. This is an idiom in this codebase, not a signal of removal.
- **`cancel` then `close` is the intended flow, not an error.** A `close` that does nothing after `cancel` is the whole point — not a dropped operation.
- **`NOP` as a field sentinel.** Throughout the wider codebase, fields of type `CancelClose` are initialized to `CancelClose.NOP` and reassigned to a live handle when a scoped operation begins, so teardown code can always call the field's `close`/`cancel` unconditionally. Treat `NOP` as the safe default.

## Caveats & gotchas

- The interface fixes only *what* the methods mean, not their thread-safety or idempotency — those come from the implementation. For the suppression use case, consult [`CancellableRelease`](CancellableRelease.md) and the [`Suppressor`](Suppressor.md) § idempotent-release notes.
- Do not call `close` manually to "force" cleanup; that defeats the deprecation contract and may double-run cleanup. If you need to trigger cleanup outside a block, use the implementation's own release path.

## Common tasks

- **Auto-cleanup you can opt out of:** `try (CancelClose c = supplier) { ...; if (handOff) c.cancel; }` — when `cancel` runs, block-exit `close` is a no-op.
- **An inert placeholder:** use `CancelClose.NOP` instead of `null`.

## Tech debt / warts

- The `@Deprecated` on `close` is a deliberate misuse of the annotation (lint nudge against manual calls), which can confuse readers into thinking the type is being retired. A custom annotation or just javadoc would express the "try-with-resources only" intent without the false deprecation signal.
- The cancel/close ordering and re-entrancy contract is left entirely to implementors; the interface javadoc does not pin down what happens if `cancel` is called *after* `close`, or concurrently.
