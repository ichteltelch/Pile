# `pile.builder` — package index (Tier 1)

Source folder: `src` (all types below).

The **fluent builders** for constructing and configuring the concrete reactive values ([`PileImpl`](../impl/PileImpl.md), [`Independent`](../impl/Independent.md), [`SealPile`](../impl/SealPile.md)). They hide the complications of dynamic-dependency recording, threaded/delayed recomputation, correctors/bounds, sealing, and remember-last-value wiring. You normally obtain a builder from a [`Piles`](../impl/Piles/_index.md) factory and finish with `.build`.

Up: [overview](../../overview.md) · [impl index](../impl/_index.md). Recompute/transaction model: [concepts/transactions.md](../../concepts/transactions.md).

## Layered structure
Interfaces define the fluent API surface (so the same configuration method returns the right self-type); abstract classes implement the actual `build` wiring; concrete classes are thin.

### Root & capability interfaces
- [`IBuilder`](IBuilder.md) — root builder interface: the small CRTP-typed common contract (`self`/`configure`/`build`/`valueBeingBuilt`/`runIfWeak`/`deferListeners`). (`name`/`owner`/`bracket` are NOT here — they're on the target interfaces.)
- [`ICorrigibleBuilder`](ICorrigibleBuilder.md) — corrector/bounds/equivalence config (`neverNull`, `corrector`, upper/lower `bounds`, `ordering`; static `applyBounds` compiles bounds into correctors + `"min"`/`"max"` associations).
- [`IListenValueBuilder`](IListenValueBuilder.md) — listener config: attach `ValueListener`s (strong/weak/value-in-scope) at build time (they attach immediately during config, not at `build`).
- [`ISealableBuilder`](ISealableBuilder.md) — seal config: `seal(...)` overloads recorded now, applied **last** in `build` (throw/ignore/warn/redirect); `makeSetter`/`giveSetter` for the privileged channel.

### Target interfaces
- [`IPileBuilder`](IPileBuilder.md) — fluent builder API for `Pile`s: recompute code, `dependOn`, dynamic dependencies, lazy, threaded/delayed recompute, equivalence.
- [`IIndependentBuilder`](IIndependentBuilder.md) — fluent builder API for `Independent` leaf values: initial value, `fromStore`/remember-last-value, correctors/bounds, sealing, valid-buffer setups.
- [`ISealPileBuilder`](ISealPileBuilder.md) — builder API for `SealPile`s (`Pile` + sealable); **hosts the `setup*` buffer/deref/field/rate-limited default methods** that the `Piles` factories delegate to.

### Abstract bases
- [`AbstractPileBuilder`](AbstractPileBuilder.md) — implements the `Pile` build logic: assembles a `Recomputer` and installs it on the pre-built `PileImpl`; dependency/bounds wiring, dynamic-dependency scouting, threaded/delayed recompute (five `MyRecomputer` variants).
- [`AbstractIndependentBuilder`](AbstractIndependentBuilder.md) — implements `Independent.build`: correctors/bounds, the **remember-last-value save/restore wiring** (restore-set → association → auto-store listener), init, sealing. (Resolved the `Independent`/`RemembersLastValue` deferral.)
- [`AbstractSealPileBuilder`](AbstractSealPileBuilder.md) — abstract base for `SealPile` builders: extends the `Pile`-builder base with deferred sealing (records `seal(...)`, applies it last in `build`). The buffer `setup*` helpers are `default`s on `ISealPileBuilder`, not here.

### Concrete builders
- [`PileBuilder`](PileBuilder.md) — concrete `Pile` builder: thin CRTP fixed-point of `AbstractPileBuilder` (binds the self-type); obtained from `Piles` factories.
- [`IndependentBuilder`](IndependentBuilder.md) — concrete `Independent` builder; thin fixed-point of `AbstractIndependentBuilder`.
- [`SealPileBuilder`](SealPileBuilder.md) — concrete `SealPile` builder; thin fixed-point of `AbstractSealPileBuilder`.

### Helper
- [`FulfillInvalid`](FulfillInvalid.md) — control-flow `RuntimeException` a recompute callback throws to finish the value as invalid (`Recomputation.fulfillInvalid`); static `r(...)` thrower helpers for expression position.
