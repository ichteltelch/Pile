# `pile.builder.PileBuilder`

The concrete, instantiable `Pile` builder — a thin CRTP fixed-point of [`AbstractPileBuilder`](AbstractPileBuilder.md) that binds the recursive self-type and supplies `self`.

Source folder: `src`. File: `pile/builder/PileBuilder.java` (~55 lines).

`PileBuilder<V extends PileImpl<E>, E> extends AbstractPileBuilder<PileBuilder<V,E>, V, E>`, declared `final`. It exists only to close the `Self` type parameter that makes [`AbstractPileBuilder`](AbstractPileBuilder.md) abstract: by passing itself as `Self`, every inherited fluent setter (`recompute`, `dependOn`, `upperBound`, …) returns `PileBuilder<V,E>` so chains stay concretely typed. See the [builder index](_index.md).

## What it adds over `AbstractPileBuilder`

Almost nothing — all build logic, the recomputer assembly, bounds/dependency wiring, and the whole fluent API live in [`AbstractPileBuilder`](AbstractPileBuilder.md) / [`IPileBuilder`](IPileBuilder.md). `PileBuilder` only contributes:

- **Constructor** `PileBuilder(V v)` — forwards to `super(v)`. As with the base, the target [`PileImpl`](../impl/PileImpl.md) **already exists**; the builder wraps and configures it, then `build` returns it.
- **`self`** — returns `this`, the fixed-point method required by the CRTP base.
- **Two `static` bound-query aliases** — `getUpperBound(HasAssociations)` / `getLowerBound(HasAssociations)`, each a pass-through to `ICorrigibleBuilder.getUpperBound`/`getLowerBound`. These read the "max"/"min" bound associations off an already-built value (the same keys `AbstractPileBuilder` writes when you call `upperBound`/`lowerBound`). They are unrelated to the builder instance — convenience statics parked on this class.

## How you obtain one

You rarely call `new PileBuilder(...)` directly. The normal entry is a [`Piles`](../impl/Piles/_index.md) factory, which constructs the `PileImpl` and hands you a builder; you then chain fluent setters and call `.build`. Configure recompute code, dependencies, bounds, etc. via the inherited API — see [`IPileBuilder`](IPileBuilder.md) for the method catalogue and [`AbstractPileBuilder`](AbstractPileBuilder.md) for what `build` actually does (recomputer dispatch, bounds-as-dependencies, threading/delay).

For `SealPile` or `Independent` targets use the sibling concrete builders (`SealPileBuilder`, `IndependentBuilder`) instead — `PileBuilder` is specifically the plain-`PileImpl` builder.

## Caveats & gotchas

- The constructor javadoc tags the parameter `@param value` but the parameter is named `v` — a harmless doc/code mismatch.
- The static `getUpperBound`/`getLowerBound` declare an unused `<E>` type parameter and return `ReadListenDependency<? extends E>` where `E` is unbound at the call site — they are thin aliases; for bound *configuration* use the instance methods `upperBound`/`lowerBound` from the base, not these.

## Tech debt / warts

- Pure boilerplate fixed-point class; nothing here is surprising. The static bound-query aliases arguably belong on `ICorrigibleBuilder` (where the real implementations live) rather than duplicated here.

## Related

- [builder index](_index.md) · [`AbstractPileBuilder`](AbstractPileBuilder.md) · [`IPileBuilder`](IPileBuilder.md) · [`PileImpl`](../impl/PileImpl.md) · [overview](../../overview.md) · recompute/transaction model: [concepts/transactions.md](../../concepts/transactions.md).
