# `pile.builder.IndependentBuilder`

The concrete, ready-to-use `Independent` builder — a thin fixed-point of [`AbstractIndependentBuilder`](AbstractIndependentBuilder.md) that just binds the CRTP self-type so the fluent API returns the right type.

Source folder: `src`. File: `pile/builder/IndependentBuilder.java`.

`IndependentBuilder<V extends Independent<E>, E>` extends `AbstractIndependentBuilder<IndependentBuilder<V,E>, V, E>` and is `final`. It is the leaf that makes the otherwise-abstract base instantiable: the base is abstract only because its `Self` type parameter is open; this class closes `Self` to itself and implements `self` to return `this`.

Up: [builder index](_index.md) · [overview](../../overview.md).

## What it adds over `AbstractIndependentBuilder`

Nothing behavioral. It contributes only:

- **Self-type binding** — `Self = IndependentBuilder<V,E>`, so every fluent setter inherited from the base (and from [`IIndependentBuilder`](IIndependentBuilder.md)) chains as `IndependentBuilder<V,E>`, not as an abstract `Self`.
- **`self`** returning `this` — the one method the base leaves abstract.
- **A public constructor** `IndependentBuilder(V value)` that forwards to `super(value)`. As in the base, `value` must be an unsealed [`Independent`](../impl/Independent.md); the base constructor rejects an already-sealed value.

All the actual configuration semantics (eager setters, the ordering-sensitive `build` pass: bounds → re-clamp listener → remember-last-value restore/store → init → seal) live in and are documented at [`AbstractIndependentBuilder`](AbstractIndependentBuilder.md). Read that doc for behavior, gotchas (`fromStore` + `init` conflict, ignored `allowInvalidation`), and the build-order pitfalls.

## How you obtain one

You normally don't `new` this directly. Builders come from a [`Piles`](../impl/Piles/_index.md) factory that hands you a configured `IndependentBuilder` over a freshly created empty `Independent`; you stack fluent config and finish with `.build`. Constructing one by hand is possible — pass an unsealed `Independent` to the constructor — but the factory route is the intended entry point (see the [builder index](_index.md)).

## Caveats & gotchas

- `build` finalizes the *same* live value the builder was created over; it is not a factory and should be called once (calling twice re-runs the deferred restore/init/seal wiring — see [`AbstractIndependentBuilder`](AbstractIndependentBuilder.md)).
- All the warts are inherited; this class adds none. See the base doc's tech-debt section.

## Related

- [`AbstractIndependentBuilder`](AbstractIndependentBuilder.md) — the base holding all behavior.
- [`IIndependentBuilder`](IIndependentBuilder.md) — the fluent API surface this type exposes.
- [`Independent`](../impl/Independent.md) — the value being built.
- [builder index](_index.md) · [overview](../../overview.md) · concepts: [`../../concepts/`](../../concepts/).
