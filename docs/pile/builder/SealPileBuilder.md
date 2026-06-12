# `pile.builder.SealPileBuilder`

Concrete, thin `SealPile` builder: the CRTP fixed point of [`AbstractSealPileBuilder`](AbstractSealPileBuilder.md) that binds the recursive `Self` type so the fluent API actually returns this concrete type.

Source folder: `src`. File: `pile/builder/SealPileBuilder.java`.

`public final class SealPileBuilder<V extends `[`SealPile<E>`](../impl/SealPile.md)`, E> extends `[`AbstractSealPileBuilder<SealPileBuilder<V,E>, V, E>`](AbstractSealPileBuilder.md). It is `final` and adds **nothing** beyond closing the `Self` type parameter.

## What it adds over `AbstractSealPileBuilder`

Only the two things the recursive type parameter forces:

- **Constructor** `SealPileBuilder(V v)` — chains to `super(v)`. All real work (the unsealed-value check that throws `IllegalArgumentException` on an already-sealed value) lives in the base constructor.
- **`self`** — returns `this`, supplying the concrete `Self` so every inherited fluent method (`seal`, `dependOn`, `setup*`, etc.) returns a `SealPileBuilder<V,E>` rather than the abstract type. This is the sole reason the base is `abstract` and this class exists.

No new fields, no new behavior, no overrides of `build`/`seal`/`makeSetter` — read [`AbstractSealPileBuilder`](AbstractSealPileBuilder.md) (deferred sealing, the `seal(...)` family, build-then-seal order) and [`ISealPileBuilder`](ISealPileBuilder.md) (the `setup*` buffer/deref/field helpers) for the actual API.

## How you obtain one

Normally you do not instantiate this directly — you get a configured builder from a [`Piles`](../impl/Piles/_index.md) factory and finish with `.build`. The constructor wraps a **fresh, unsealed** `SealPile`; handing it an already-sealed value throws (base constructor).

## Caveats & gotchas

- The class javadoc says it implements `IIndependentBuilder` — that is a **copy-paste slip**; it builds `SealPile`s, not `Independent`s. Reported below.
- Single-shot in practice: don't reuse across `build`s when sealing (see [`AbstractSealPileBuilder` caveats](AbstractSealPileBuilder.md)).

## Tech debt / warts

- **Wrong type in javadoc** — references `IIndependentBuilder` instead of `ISealPileBuilder`.
- **Doubled/empty javadoc** — two stray `/**` opener lines and empty `@param <V>`/`@param <E>` tags, consistent with the [project-wide note on unsystematic API](../../overview.md).
- Trailing blank lines at the end of the class body.

## Related

- [`AbstractSealPileBuilder`](AbstractSealPileBuilder.md) — the base; all build/seal logic.
- [`ISealPileBuilder`](ISealPileBuilder.md) — the interface; hosts `setup*` helpers.
- [`SealPile`](../impl/SealPile.md) — the value being built.
- [builder index](_index.md) · [overview](../../overview.md) · [concepts/](../../concepts/).
