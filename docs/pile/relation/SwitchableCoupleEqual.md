# `SwitchableCoupleEqual`

A [`CoupleEqual`](CoupleEqual.md) (two-way equality coupling) that can be switched on/off at runtime via a reactive boolean and [`Suppressor`](../aspect/suppress/_index.md)s.

Source folder: `src`. Package `pile.relation`.

Up: [overview](../../overview.md) · [relation index](_index.md). Base: [`CoupleEqual`](CoupleEqual.md). Switch machinery: [`SwitchableRelation`](SwitchableRelation.md), [`ImplSwitchableRelation`](ImplSwitchableRelation.md).

## What it is — a thin delta over `CoupleEqual`

`SwitchableCoupleEqual<E>` **extends** `CoupleEqual<E>` (so it inherits the whole equality-coupling behavior) and **implements** `SwitchableRelation<ReadListenValue<Boolean>>`. It adds nothing to the coupling logic; it only makes the coupling **conditionally active**.

The trick is delegation, not inheritance of the switch: the constructor passes `enabled = false` to `super(op1, op2, false, mode)` so the base does **not** install its own always-on listener, and instead the switchability is supplied by a held delegate `switcher` (an `ImplSwitchableRelation`). Every `SwitchableRelation` method on this class just forwards to `switcher` — `disable`, `isEnabled`, `shouldBeEnabled`, `setShouldBeEnabled`, `isEnabledPrim`, `onlyOnChanges`, `shouldActOnlyOnOperandChanges`.

## How the switch is wired

The base `CoupleEqual`/`AbstractRelation` machinery drives enable/disable; this class only routes the *enabled-ness signal* through the delegate:

- **Switch source.** Enablement is the conjunction of a reactive `shouldBeEnabled` boolean and the absence of active `Suppressor`s. Both live in `switcher` (`ImplSwitchableRelation`). The constructor stores `shouldBeEnabled` via `switcher.setShouldBeEnabled(...)`.
- **`isEnabled()`** returns the delegate's reactive boolean — the effective on/off state the relation watches.
- **`disable()`** returns a `Suppressor`; holding it forces the relation off until released (one of several suppressors AND-ed into enabled-ness).
- **`isEnabledPrim()`** is null-guarded (`switcher != null && switcher.isEnabled().isTrue()`) because it can be queried during base-class construction before `switcher` is assigned.

## Re-assertion on re-enable

After delegating the switch, the constructor calls `installEnabledListener()` (inherited from `AbstractRelation`). That method:

- adds a listener on `isEnabled()` (only if it isn't the constant `Piles.TRUE`); when enabled-ness changes and `!shouldActOnlyOnOperandChanges()`, it calls the relation's listener via `runImmediately()`;
- if currently enabled and not operand-only, runs the listener once immediately.

So **re-enabling re-asserts the equality** right away — the moment the switch flips back on, the coupling listener fires and re-equalizes the two operands (it does not wait for the next operand change). This only happens when `shouldActOnlyOnOperandChanges()` is false; with `onlyOnChanges(true)` set, the relation acts solely on operand changes and the re-enable does **not** trigger an immediate re-assert (see `onlyOnChanges`).

## Key methods (all forward to `switcher`)

- `setShouldBeEnabled(ReadListenValue<Boolean>)` — swap the reactive enable source.
- `onlyOnChanges()` / `onlyOnChanges(boolean)` — fluent; return `this`. Make the relation react only to operand changes (suppresses the on-enable re-assert described above).
- `shouldActOnlyOnOperandChanges()` — the flag `installEnabledListener` consults.

## Caveats & gotchas

- **`enabled=false` to `super` is essential.** The base constructor must not wire its own listener; this class re-wires switchability through `switcher` instead. Do not "fix" that `false`.
- **`isEnabledPrim` null-guard** reflects that the base constructor may consult enabled-ness before `switcher` exists; treat enabled as `false` in that window.
- Releasing a `disable()` `Suppressor` (or `shouldBeEnabled` going true) flips `isEnabled()` and re-asserts equality immediately unless `onlyOnChanges` is set.

## Tech debt / warts

- The four switchable methods are duplicated verbatim with [`SwitchableImplication`](SwitchableImplication.md) (same forward-to-`switcher` boilerplate). A shared mixin/base could remove the copy-paste, but Java single-inheritance (each already extends its concrete relation) is why it's hand-forwarded.
