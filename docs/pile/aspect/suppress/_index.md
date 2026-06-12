# `pile.aspect.suppress` — package index (Tier 1)

Source folder: `src` (all types below).

The **suppression / scoped-handle utilities**. The headline type is [`Suppressor`](Suppressor.md) — the "release-to-undo" handle returned by `transaction`, `suppressAutoValidation`, deep-revalidation suppression, `suppressRememberLastValue`, etc. The rest are the closeable base, the scope-block used by the framework's `with*` thread-local overrides, and the collective on/off switchers.

Up: [aspect index](../_index.md) · [overview](../../../overview.md).

## Types
- [`Suppressor`](Suppressor.md) — the release-to-undo handle (idempotent `release`, GC-tied `wrapWeak`, `NOP`).
- [`SafeCloseable`](SafeCloseable.md) — an `AutoCloseable` whose `close` throws no checked exception (base of `Suppressor`/`MockBlock`); has a `NOP`.
- [`MockBlock`](MockBlock.md) — a try-with-resources scope object (with a `NOP`) returned by the framework's `with*` methods to scope a thread-local override (set on enter, restore on close); thread-bound, idempotent close, expected LIFO.
- [`CancelClose`](CancelClose.md) — a try-with-resources closeable whose pending `close` can be neutralized by calling `cancel` first (a close-you-can-cancel).
- [`CancellableRelease`](CancellableRelease.md) — a one-shot cancellable release for a `Suppressor` (from `Suppressor.cancellableRelease`); `cancel` makes the auto-release a no-op so the suppression can be handed off.
- [`SuppressionSwitcher`](SuppressionSwitcher.md) — a collective on/off switch that suppresses/un-suppresses a whole group together via one group `Suppressor` (make-before-break, `wrapWeak`-ed); built via `makeSwitcher`.
- [`ReactiveSuppressionSwitcher`](ReactiveSuppressionSwitcher.md) — a `SuppressionSwitcher` whose on/off state follows a reactive boolean (via a weak listener); built by `makeReactiveSwitcher`.
