# `SuppressionSwitcher`

A collective on/off switch that suppresses or un-suppresses a whole collection of objects together by holding/releasing one [`Suppressor`](Suppressor.md) for the group.

Source folder: `src` — package `pile.aspect.suppress`.

A `SuppressionSwitcher<E>` bundles three things: **a collection of objects** to suppress, **a `Function<E, Suppressor>`** that says *how* to suppress one object, and **a boolean** "are they currently suppressed". Flip the boolean or swap the collection and it lazily creates/releases the right `Suppressor`s underneath. It is the imperative counterpart of [`ReactiveSuppressionSwitcher`](ReactiveSuppressionSwitcher.md) (which is driven by a reactive boolean instead of explicit setter calls).

Up: package [`_index.md`](_index.md) · overview [`../../../overview.md`](../../../overview.md).

## What it's for

You have several suppressibles (e.g. a set of [`AutoValidationSuppressible`](../AutoValidationSuppressible.md)s or [`LastValueRememberSuppressible`](../LastValueRememberSuppressible.md)s) and you want to suppress them all as a unit, then later release them all, and possibly change *which* objects are in the set without churning the suppression on/off. `SuppressionSwitcher` holds a single live `Suppressor` (`current`) for the whole group and rebuilds it whenever the group or the state changes.

It does **not** know what "suppress" means — that is entirely the injected `method` (`Function<? super E, ? extends Suppressor>`). So the same class switches auto-validation, last-value-remembering, or anything else that hands out a `Suppressor`.

### Who creates it

You do not normally call the (protected) constructor directly. The aspect interfaces expose `makeSwitcher`-style factories that pre-bind `method` to the right reified suppress-function and return a `SuppressionSwitcher.Final`. For example `AutoValidationSuppressible.makeSwitcher` builds a `SuppressionSwitcher.Final` driven by `SUPPRESS_AUTO_VALIDATION` (see [`AutoValidationSuppressible.md`](../AutoValidationSuppressible.md) § bulk and switched suppression, `AutoValidationSuppressible.java`). `LastValueRememberSuppressible` exposes the analogous switcher for its own suppress-function.

## The two axes: state and items

Every public method moves the switcher along one or both of two independent axes:

- **state** (`boolean state`, ) — *should* the current items be suppressed right now.
- **items** (`Collection<? extends E> suppressThese`, ) — *which* objects the switch governs.

| Method family | Changes state? | Changes items? |
|---|---|---|
| `setSuppressedState(boolean)` | yes | no |
| `setSuppressedItems(...)` (collection / varargs / single / none) | no | yes |
| `setSuppressed(boolean, ...)` | yes | yes |
| `getSuppressedState` | — (read) | — |

All mutators are `synchronized` and return `this` for chaining.

### `setSuppressedState(boolean)` — flip on/off, keep the items

The "switch" in the name. If the new value equals the current `state`, it does nothing. Turning **on** calls the private `suppress` (builds new `Suppressor`s for the current items); turning **off** calls `current.release`.

### `setSuppressedItems(...)` — swap the governed objects

Overloads: a `Collection`, a single object, varargs (`E...`), and a no-arg form. They change `suppressThese` **without** changing `state`; if `state` is currently on, they rebuild the suppressors for the new items (new suppressors are created *before* the old ones are released — see "make-before-break" below). Variants:

- `setSuppressedItems(Collection)` / `setSuppressedItems(Collection, boolean equalsTest)` — the `equalsTest=true` default short-circuits (does nothing) when the new collection `.equals` the old one.
- `setSuppressedItems(E that)` — singleton; `null` clears all. Has a fast path: if the current set is already exactly `{that}` (identity-compared), it returns without rebuilding.
- `setSuppressedItems(E... these)` — wraps `Arrays.asList`.
- `setSuppressedItems` (no args) — clears the items and releases `current`, but **leaves `state` untouched**.

### `setSuppressed(boolean, ...)` — change both at once

The combined setters. `setSuppressed(boolean newState)` (no items) clears the items, releases, and sets `state`. The richer overloads (`Collection`, single, varargs, with optional `equalsTest`) set the new items and the new state in one shot. When `equalsTest` matches the existing collection they delegate to `setSuppressedState(newState)` so only the state axis moves.

## Salient / surprising behavior

### Make-before-break

`suppress` and the item-swap setters create the **new** `Suppressor`(s) first, and only then `release` the previous `current`. This is deliberate: it keeps the suppressed behavior continuously suppressed across a set-change, with no momentary gap where the value could (e.g.) auto-recompute. The class Javadoc states this ("released after the new `Suppressor`s have been created", , , …).

### The held `Suppressor` is always `wrapWeak`-ed

`suppress` wraps every freshly created suppressor in `.wrapWeak`. So if the `SuppressionSwitcher` itself is dropped without releasing, the suppression is released on GC rather than leaking forever — the standard GC safety-net (see [`Suppressor.md`](Suppressor.md) § GC-tied release). Note this is the **no-warning** `wrapWeak`, so a forgotten switcher releases silently.

### `current` is never null; it starts at `NOP`

`current` is initialised to `Suppressor.NOP` and re-assigned only inside `suppress`. The "release" paths (`setSuppressedState(false)`, `setSuppressedItems`, etc.) call `current.release` but **do not reset `current` to `NOP`**. Because `release` is idempotent that is harmless, but it means a released `current` lingers as the field value until the next `suppress` replaces it.

### `suppress` only acts when state is on

`suppress` is the single place suppressors are built. The item-swap setters call it **only if `state` is currently true**; if the switch is off, changing the items just records them for later. `setSuppressedState`/`setSuppressed` call it when turning on.

### Empty / singleton fast paths in `suppress`

- empty or null items → just `current.release` (nothing to suppress).
- exactly one item → `method.apply(item).wrapWeak`.
- many items → `Suppressor.many(method, suppressThese).wrapWeak` (one composite handle releasing all).

## Caveats & gotchas

- **Collections must not be mutated externally.** The `equalsTest` short-circuit (and the singleton fast path) assume the collections you pass in are not modified after handing them over. The Javadoc repeatedly warns: "Call this method only if you're exclusively using collections that are not modified." If you mutate a collection in place, an `equalsTest` may wrongly conclude "no change" and skip rebuilding.
- **`setSuppressedItems` (no-arg) does not turn the switch off** — it only empties the set and releases the live suppressor; `state` stays whatever it was. If you later `setSuppressedItems(...)` new items while `state` is still on, they get suppressed. Use `setSuppressed(false)` if you actually want the switch off.
- **`equalsTest` uses `Objects.equals` on the collections**, i.e. collection `.equals` semantics — a `List` and a `Set` with the same elements are *not* equal, and the varargs/singleton forms build `List`/singleton wrappers, so mixing entry styles can defeat the short-circuit.
- **Not safe to share a switcher across the wrong suppress-function.** The `method` is fixed at construction; one switcher governs one kind of suppression. For a different aspect, make another switcher via that aspect's factory.

## Common tasks (how to…)

- **Get one** — call the aspect's factory, e.g. `AutoValidationSuppressible.makeSwitcher` (returns a `SuppressionSwitcher.Final`), not the constructor.
- **Suppress a fixed group, toggling on/off over time:**
  `sw.setSuppressedItems(values); … sw.setSuppressedState(true); … sw.setSuppressedState(false);`
- **Change the group while staying suppressed (no recompute gap):**
  with `state==true`, call `sw.setSuppressedItems(newValues)` — new suppressors are taken before the old are released.
- **Switch both group and state in one call:** `sw.setSuppressed(true, newValues)`.
- **Stop suppressing entirely:** `sw.setSuppressed(false)` (clears items, releases, sets state off). `sw.setSuppressedItems` releases but leaves `state` on — usually not what you want.

## Tech debt / warts

- **`Final` is the only thing ever instantiated**; the base class exists solely so `makeSwitcher` can return the `final` subclass "slightly more efficient". The non-final base has no other subclass in the library.
- **Javadoc warts:** the class doc has an empty `<li></li>` and a bare `@param <E>`; several `@return` tags are bare; typos in the `equalsTest` docs ("Set this to `true` omly", "using collections" left dangling, , ).
- **No reactive read of the state** — unlike [`ReactiveSuppressionSwitcher`](ReactiveSuppressionSwitcher.md), there is no observable for `state`; `getSuppressedState` is a plain snapshot.
- **`current` not reset to `NOP` on release** (see above) — benign given idempotent `release`, but the field can hold a released suppressor.

## Related

- [`Suppressor`](Suppressor.md) — the handle this class holds/releases (`many`, `wrapWeak`, `NOP` all used here).
- [`ReactiveSuppressionSwitcher`](ReactiveSuppressionSwitcher.md) — the reactive (boolean-driven) sibling; built via `makeReactiveSwitcher`.
- [`AutoValidationSuppressible`](../AutoValidationSuppressible.md) — the most common source of a switcher (`makeSwitcher` → this class).
- [`LastValueRememberSuppressible`](../LastValueRememberSuppressible.md) — another aspect that hands out a switcher.
- [overview.md](../../../overview.md) — Pile architecture map.
