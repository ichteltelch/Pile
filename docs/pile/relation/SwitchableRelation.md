# `SwitchableRelation`

The interface for a relation that can be turned on/off at runtime, gating whether it enforces its invariant.

Source folder: `src` — package `pile.relation`.

Up: [package index](_index.md) · [overview](../../overview.md). Standard implementation: [`ImplSwitchableRelation`](ImplSwitchableRelation.md). Concrete users: [`SwitchableCoupleEqual`](SwitchableCoupleEqual.md), [`SwitchableImplication`](SwitchableImplication.md). Base class for relations: [`AbstractRelation`](AbstractRelation.md).

## What it's for

A plain relation (see [the package index](_index.md)) permanently watches its participants and writes back to keep some property true. `SwitchableRelation<Sw>` adds a runtime kill-switch: while it is "off", the relation stops enforcing its invariant; when it turns back "on", **implementation-specific measures re-establish the invariant** (the javadoc on `disable` is explicit that re-activation must re-assert validity — it is not left stale). This is the contract its users (`SwitchableCoupleEqual`, `SwitchableImplication`) rely on.

The type parameter `Sw` is the type of the *extra* enable-control object (see `shouldBeEnabled` / `setShouldBeEnabled`). In the standard implementation this is concretely `ReadListenValue<Boolean>` — a reactive boolean. The interface leaves it generic so an implementation could use a different control object.

## The two on/off mechanisms

Enablement is governed by **two independent inputs**, AND-ed together:

1. **Suppressors** — `disable()` returns a [`Suppressor`](../aspect/suppress/_index.md). The relation is held off for as long as any handed-out `Suppressor` is unreleased (they stack — last release wins). This is the imperative, scoped "pause this for a while" channel.
2. **The `shouldBeEnabled` control object** (`Sw`) — a longer-lived, declarative "should this be on at all" input. In the standard impl it is a reactive `Boolean`; a `false`/`null`/invalid value also forces the relation off.

`isEnabled()` is the *resolved* answer: a reactive `ReadListenDependencyBool` that is true only when no suppressor is active **and** `shouldBeEnabled` resolves to true.

## Key methods (by purpose)

- `disable()` → `Suppressor` — acquire a scoped off-switch; release it (or all of them) to allow re-enabling.
- `isEnabled()` → `ReadListenDependencyBool` — the resolved, observable on/off state. Listen to this to react to toggles.
- `shouldBeEnabled()` / `setShouldBeEnabled(Sw)` — get/replace the extra declarative enable-control object.
- `onlyOnChanges(boolean)` / `onlyOnChanges()` / `shouldActOnlyOnOperandChanges()` — configure whether the relation should act only when an operand actually changes (vs. on every fired event). Note `onlyOnChanges(...)` returns `SwitchableRelation<ReadListenValue<Boolean>>` — i.e. it narrows `Sw` to the concrete reactive-boolean control type, so call it before relying on a more general `Sw`.

## How subclasses / users plug in

The intended pattern (stated in `ImplSwitchableRelation`'s javadoc) is **composition, not inheritance**: a concrete `SwitchableRelation` holds an `ImplSwitchableRelation` as a field and forwards `disable` / `isEnabled` / `shouldBeEnabled` / `setShouldBeEnabled` / `onlyOnChanges` / `shouldActOnlyOnOperandChanges` to it. The concrete relation supplies the actual enforcement logic and gates it on the mixin's `isEnabled` value. See [`SwitchableCoupleEqual`](SwitchableCoupleEqual.md) and [`SwitchableImplication`](SwitchableImplication.md) for the two in-tree users; see [`AbstractRelation`](AbstractRelation.md) for the (separate) base that handles participant registration and teardown.

## Caveats & gotchas

- **Two gates, AND-ed.** A relation is enabled only when *both* no suppressor is active and `shouldBeEnabled` is true/valid. Releasing every suppressor does **not** enable the relation if `shouldBeEnabled` is false; conversely, setting `shouldBeEnabled` true does nothing while a suppressor is held.
- **Re-enable re-asserts the invariant.** Toggling off then on is not a no-op — the implementation is contractually required to repair the relation on re-activation. Do not assume the participants kept their constraint while disabled.
- **`onlyOnChanges()` changes the static type of the relation** (narrows `Sw`). It is fluent (returns the relation) but the return type is the concrete `SwitchableRelation<ReadListenValue<Boolean>>`.

## Tech debt / warts

- Several javadoc `@return` / sentences are truncated or stubbed ("A reactive value that tells whether this"), so the contract leans on the implementation. The mechanism is small enough that this doc + [`ImplSwitchableRelation`](ImplSwitchableRelation.md) cover it.
- The generic `Sw` is, in practice, always `ReadListenValue<Boolean>`; the genericity buys little and the only mixin (`ImplSwitchableRelation`) fixes it to that type.
