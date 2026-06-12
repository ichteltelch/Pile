# `ImplSwitchableRelation`

The standard `SwitchableRelation` implementation — a reusable mixin held as a field by concrete switchable relations and forwarded to.

Source folder: `src` — package `pile.relation`.

Up: [package index](_index.md) · [overview](../../overview.md). Interface: [`SwitchableRelation`](SwitchableRelation.md). Users: [`SwitchableCoupleEqual`](SwitchableCoupleEqual.md), [`SwitchableImplication`](SwitchableImplication.md). Relation base: [`AbstractRelation`](AbstractRelation.md).

## What it's for

`ImplSwitchableRelation implements SwitchableRelation<ReadListenValue<Boolean>>` and "implements most of the logic for making relations switchable". It is **not a base class** — the javadoc says to use it *as a field* of a concrete `SwitchableRelation`, which forwards the switchability methods to it. The concrete relation owns the actual invariant-enforcement code and gates it on this mixin's `isEnabled` value.

## State and the resolved enable-flag

- `isEnabled` — a sealed `IndependentBool` built via `Piles.independent(true)` (see [`Piles`](../impl/Piles/_index.md)). It is the **resolved, observable** enable state returned by `isEnabled()`. It is sealed and exposes its setter only internally: the builder's `.giveSetter(s -> setEnabled = s)` captures the package-private setter into `setEnabled`, and the value is `seal()`ed so outside code cannot write it directly. All recomputation of the flag is done by hand and pushed through `setEnabled.accept(...)`.
- `suppressors` (int) — count of currently-held `disable()` suppressors, guarded by `mutex`.
- `shouldBeEnabled` — the declarative control value (`ReadListenValue<Boolean>`), default `Piles.TRUE`.
- `onlyOnChanges` (boolean) — backs `shouldActOnlyOnOperandChanges()`.

The resolved rule (computed identically in `disable`, the suppressor-release callback, and `sbeChanged`): **enabled = `suppressors == 0` AND `shouldBeEnabled` is valid AND resolves to a non-null `true`.** Any of {suppressor held, `shouldBeEnabled` invalid, value null, value false, `InvalidValueException`} forces `false`. Invalid/exception/null all degrade to `false` rather than propagating.

## How toggling works

### `disable()`

Returns a `Suppressor` (wrapped weak via `Suppressor.wrap(...).wrapWeak()`). Acquiring it increments `suppressors`; on the **first** suppressor (`suppressors == 1`) it computes the would-be value and (outside the lock) pushes `false` through `setEnabled` — actually it computes `v` from `shouldBeEnabled` but, since a suppressor is now held, the *effective* enabled state is off; the immediate `setEnabled.accept(v)` reflects the suppressor gate via the release path's logic. Releasing the returned `Suppressor` decrements `suppressors`; when it hits `0` it recomputes from `shouldBeEnabled` and re-enables if that resolves to true. The whole release body runs under `mutex`; the final `setEnabled.accept` happens after.

Because re-enabling flips `isEnabled` back to `true`, any listener the concrete relation has attached to `isEnabled()` fires and **re-asserts the invariant** — that is the "implementation-specific measures" the [`SwitchableRelation`](SwitchableRelation.md) contract promises. This mixin's job is purely to drive `isEnabled`; the concrete relation does the actual repair in its `isEnabled` listener.

### `setShouldBeEnabled(ReadListenValue<Boolean>)`

Swaps the declarative control value. It detaches the old weak `ValueListener` (`removeHandle`) from the previous `shouldBeEnabled`, stores the new one, and — unless the new value `willNeverChange()` (e.g. a `Constant`) — attaches `sbeChanged` as a **weak** value listener (`addWeakValueListener`). It then calls `sbeChanged.runImmediately(true)` to recompute the flag from the new control value at once. Null is rejected (`Objects.requireNonNull`); re-setting the same instance is a no-op.

### `sbeChanged`

The listener on the current `shouldBeEnabled`. On any change it recomputes the resolved flag (suppressor-and-validity rule above) and pushes it through `setEnabled`.

### `onlyOnChanges(boolean)`

Sets the `onlyOnChanges` flag and returns `this`. When set to **false**, it calls `isEnabled.fireValueChange()` to force a re-fire — so downstream listeners re-evaluate even without a value change. When `true`, no re-fire.

## Caveats & gotchas

- **Weak listeners.** Both `addWeakValueListener` (on `shouldBeEnabled`) and `Suppressor.wrapWeak()` mean the mixin does not keep these alive on its own. The owning relation must retain strong references to whatever it needs (standard Pile weak-listener discipline).
- **`shouldBeEnabled` defaults to `Piles.TRUE`**, a constant that `willNeverChange()`, so no listener is attached until you `setShouldBeEnabled` a changeable value. With the default, only the suppressor channel can turn the relation off.
- **`mutex` discipline.** All `suppressors`/`shouldBeEnabled` reads for the flag computation are done under `mutex`; the actual `setEnabled.accept(...)` is deliberately performed *outside* the lock to avoid running listener callbacks while holding it. Keep that ordering if you touch this class.
- **`isEnabled` is sealed.** External code cannot set it; only the captured `setEnabled` consumer can. Drive enablement through `disable()` / `setShouldBeEnabled`, never by trying to write `isEnabled()`.
- Invalid or exceptional `shouldBeEnabled` silently resolves to `false` (off). This is idiomatic degrade-to-safe, not an error path you can observe.

## Common tasks

- **Make a relation switchable:** give the concrete relation an `ImplSwitchableRelation` field; forward `disable`/`isEnabled`/`shouldBeEnabled`/`setShouldBeEnabled`/`onlyOnChanges`/`shouldActOnlyOnOperandChanges` to it; in the relation's setup, add a listener to `field.isEnabled()` that enforces the invariant when it becomes true. See [`SwitchableCoupleEqual`](SwitchableCoupleEqual.md) / [`SwitchableImplication`](SwitchableImplication.md).
- **Drive enablement declaratively:** `setShouldBeEnabled(someReactiveBool)` — the relation tracks that boolean (weakly) thereafter.
- **Pause temporarily:** hold the `Suppressor` from `disable()`; release it to resume (and re-assert).

## Tech debt / warts

- In `disable()`, when acquiring the first suppressor the code computes `v` from `shouldBeEnabled` and only pushes it if non-null, but a freshly-held suppressor should force `false`; the first-acquire branch's value computation mirrors the release branch rather than just setting `false`. The observable effect is still "off" (the suppressor gate dominates on the next recompute), but the first-acquire `setEnabled.accept(v)` path is convoluted and easy to misread. See `SUSPECTED_BUGS` in the combined report.
- Heavy duplication of the same "resolve to boolean, degrade invalid/null to false" snippet across `disable` (twice) and `sbeChanged` — a private helper would remove three copies.
