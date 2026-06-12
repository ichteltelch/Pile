# `ReactiveSuppressionSwitcher`

A [`SuppressionSwitcher`](SuppressionSwitcher.md) whose on/off state is driven by a **reactive boolean** instead of a plain `boolean`: suppression automatically follows the boolean's value via a listener.

Source folder: `src` · package `pile.aspect.suppress` · `final class ReactiveSuppressionSwitcher<E> extends SuppressionSwitcher<E>`.

Up: [package index](_index.md) · [overview](../../../overview.md) · siblings [`SuppressionSwitcher`](SuppressionSwitcher.md), [`Suppressor`](Suppressor.md).

## What it adds over the plain switcher

The base [`SuppressionSwitcher`](SuppressionSwitcher.md) toggles a held [`Suppressor`](Suppressor.md) on/off via an explicit `boolean`. This subclass keeps that machinery but adds a layer: a `ReadListenValue<? extends Boolean>` (`reactiveState`, default `Piles.FALSE`) that *is* the source of truth for the on/off state. Whenever that boolean changes, a listener re-derives the inherited `state` and calls `super.setSuppressedState(...)`. So you wire a switcher to a reactive condition once, and suppression tracks it for the lifetime of the binding.

Created via the `makeReactiveSwitcher`-style factories (parallel to the base type's `makeSwitcher`); the constructor takes the same suppression `method` (`Function<E, Suppressor>`) as the base.

## Listener wiring (the core mechanism)

- `updater` is a `ValueListener` that, under `synchronized(this)`, calls `super.setSuppressedState(ReadValueBool.isTrue(reactiveState))` — i.e. reads the current boolean and pushes it into the base switcher's plain on/off logic.
- Binding happens in `_setSuppressedState(s, update)`: it removes the old weak listener (via the saved `remove` key), swaps in the new `reactiveState`, and re-adds via `addWeakValueListener(updater)` — keeping the returned **removal key** in `remove`. If `update` is true it calls `updater.runImmediately` to sync state right away.
- The listener is registered **weakly** (`addWeakValueListener`, ). The switcher itself holds a strong reference to `updater`, so the binding survives as long as the switcher is reachable; the weak registration just lets the reactive value avoid pinning the switcher. The `remove` field is the key required to detach a weak listener (see [`ListenValue`](../listen/) `addWeakValueListener`).
- `null` boolean is normalized to `Piles.FALSE`; re-setting the same instance is a no-op.

## Methods by purpose

The public API mirrors the base but **overloads each mutator to accept a `ReadListenValue<? extends Boolean>`** in place of the plain `boolean`:

- `setSuppressedState(ReadListenValue<Boolean>)` / `setSuppressedState(boolean)` — change only the controlling boolean (the `boolean` form wraps the value in `Piles.getConstant`, giving a constant-driven binding). Replaces the reactive source and syncs immediately.
- `setSuppressed(...)` overloads — also clear/replace the suppressed objects, then rebind the boolean. Forms take a `Collection`, varargs `E...`, a single `E`, plus an `equalsTest` flag, exactly like the base.
- Inherited `getSuppressedState` still returns the last *applied* `boolean state`, not the live reactive value.

All mutators return `this` for chaining and are `synchronized`.

## Caveats & gotchas

- **Constant boolean = no reactivity.** The `boolean`-taking overloads wrap the argument with `Piles.getConstant`, so the binding never fires again — you get a one-shot value identical to using the plain base switcher. Pass a real reactive boolean to get live tracking.
- **`getSuppressedState` can lag / disagree.** It returns the inherited `state` field, which is only updated when the listener runs (or on `runImmediately`). It reflects the last applied state, not necessarily the boolean's current value if you read it from another thread mid-change.
- **Weak listener lifetime.** Suppression tracks the boolean only while the switcher is strongly reachable. Drop the switcher and the weak listener can be collected and updates silently stop — hold a reference for as long as you want the binding live. (This is by design, not a bug.)
- **`_setSuppressedState` always re-binds on a *different* instance**, even if the two reactive values are logically equal; identity (`==`) is the dedup test.

## Tech debt / warts

- `setSuppressed(ReadListenValue, ...)` and `setSuppressed(boolean)` (no-collection forms) at  call `super.setSuppressed(state)` — passing the **inherited `state` field**, not the new boolean. See SUSPECTED_BUGS below; this looks like it should pass the freshly requested state.
- Class javadoc is the base class's, copy-pasted, with a dangling empty `<li></li>` bullet and the typo "rective"; the `@return` tags on `void`/builder methods are vestigial.
