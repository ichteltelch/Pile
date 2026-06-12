# `SwitchableImplication`

An [`Implication`](Implication.md) (enforced material implication, if-A-then-B) that can be switched on/off at runtime via a reactive boolean and [`Suppressor`](../aspect/suppress/_index.md)s.

Source folder: `src`. Package `pile.relation`.

Up: [overview](../../overview.md) · [relation index](_index.md). Base: [`Implication`](Implication.md). Switch machinery: [`SwitchableRelation`](SwitchableRelation.md), [`ImplSwitchableRelation`](ImplSwitchableRelation.md).

## What it is — a thin delta over `Implication`

`SwitchableImplication` **extends** `Implication` (inheriting the full implication-enforcement behavior) and **implements** `SwitchableRelation<ReadListenValue<Boolean>>`. It adds no implication logic; it only makes enforcement **conditionally active**.

Switchability is supplied by a held delegate `switcher` (a `final ImplSwitchableRelation`), not by the base. The constructor calls `super(premise, conclusion, onConflictKeepPremise)`, then builds `switcher` and stores `shouldBeEnabled` into it. Every `SwitchableRelation` method forwards to `switcher` — `disable`, `isEnabled`, `shouldBeEnabled`, `setShouldBeEnabled`, `isEnabledPrim`, `onlyOnChanges`, `shouldActOnlyOnOperandChanges`.

(Note: unlike [`SwitchableCoupleEqual`](SwitchableCoupleEqual.md), the `Implication` super-constructor here takes no enabled flag — `SwitchableImplication` relies on the `switcher` for enabled-ness directly.)

## How the switch is wired

- **Switch source.** Enabled-ness is the conjunction of a reactive `shouldBeEnabled` boolean and the absence of active `Suppressor`s, both held in `switcher` (`ImplSwitchableRelation`). The constructor seeds it via `switcher.setShouldBeEnabled(shouldBeEnabled)`.
- **`isEnabled()`** returns the delegate's reactive boolean (the effective on/off state).
- **`disable()`** returns a `Suppressor`; while held it forces the implication off (AND-ed with the other suppressors and `shouldBeEnabled`).
- **`isEnabledPrim()`** is `switcher.isEnabled().isTrue()` — **not** null-guarded here, since `switcher` is `final` and the `Implication` super-constructor doesn't query enabled-ness before it's assigned (contrast `SwitchableCoupleEqual.isEnabledPrim`, which is guarded).

## Re-assertion on re-enable

The constructor finishes with `installEnabledListener()` (inherited from `AbstractRelation`): it listens on `isEnabled()` (unless that's the constant `Piles.TRUE`) and, when enabled-ness changes and `!shouldActOnlyOnOperandChanges()`, re-runs the relation's listener via `runImmediately()`; it also runs it once immediately if currently enabled.

So **flipping the switch back on re-enforces the implication immediately** — the conclusion is corrected at once rather than waiting for the next premise/conclusion change. With `onlyOnChanges(true)` set, the relation acts only on operand changes and the on-enable re-assert is suppressed.

## Key methods (all forward to `switcher`)

- `setShouldBeEnabled(ReadListenValue<Boolean>)` — swap the reactive enable source.
- `onlyOnChanges()` / `onlyOnChanges(boolean)` — fluent; return `this`. React only to operand changes (suppresses the on-enable re-assert).
- `shouldActOnlyOnOperandChanges()` — the flag `installEnabledListener` consults.

## Caveats & gotchas

- The `onConflictKeepPremise` policy (which side wins when premise and conclusion disagree) is inherited unchanged from [`Implication`](Implication.md); switchability is orthogonal to it.
- Releasing a `disable()` `Suppressor` (or `shouldBeEnabled` going true) re-enforces the implication immediately unless `onlyOnChanges` is set.

## Tech debt / warts

- The switchable-method bodies are duplicated verbatim with [`SwitchableCoupleEqual`](SwitchableCoupleEqual.md) (same forward-to-`switcher` boilerplate). Java single-inheritance (each already extends its concrete relation) forces the hand-forwarding; a shared helper could de-duplicate.
