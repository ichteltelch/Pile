# `IBuilder`

The root builder interface: the small common contract every fluent Pile builder shares, parameterized by a CRTP self-type so configuration calls return the concrete builder.

Source folder: `src` · package `pile.builder` · file `IBuilder.java`.

Up: [builder index](_index.md) · [impl index](../impl/_index.md) · [overview](../../overview.md).

## What it's for

`IBuilder<Self, V>` is the base of the whole fluent-builder hierarchy. It declares only the handful of operations common to *all* builders (the build lifecycle, `configure`, weak-cleanup registration, listener deferral) and — crucially — the `self` hook that powers the self-type pattern. The richer configuration (recompute, brackets, name, owner, bounds, sealing, listeners) lives on the capability/target sub-interfaces, not here.

You rarely name this type directly; you obtain a concrete builder from a [`Piles`](../impl/Piles/_index.md) factory, chain configuration, and call `.build`.

## The self-type (CRTP) pattern

The first type parameter is recursive: `interface IBuilder<Self extends IBuilder<Self, V>, V>`. Every fluent method returns `Self`, and `self` (the one method an implementor must supply to satisfy this contract) returns `this` as the concrete builder type. This is curiously-recurring template pattern (CRTP) adapted to Java generics: it lets a method declared *here* (e.g. `configure`, `runIfWeak`, `deferListeners`) return the **concrete** builder type, so chaining `Piles.….recompute(…).name("x").seal` stays on the concrete type and every sub-interface's method remains reachable without casts.

Each sub-interface re-binds `Self` to itself and adds the content type(s):
- `ICorrigibleBuilder<Self, V extends CorrigibleValue<E>, E>` — adds `E` (content type) for bounds/correctors.
- `IListenValueBuilder<Self, V extends ListenValue>` — listener config.
- `ISealableBuilder<Self, V extends Sealable<E>, E>` — seal config.
- `IPileBuilder<Self, V extends Pile<E>, E>` — recompute / dependencies / name / brackets.
- `IIndependentBuilder` — extends `ICorrigibleBuilder` **+** `IListenValueBuilder` **+** `ISealableBuilder` (multiple inheritance of capability interfaces).
- `ISealPileBuilder` — `Pile` + sealable.

The abstract bases (`AbstractPileBuilder`, `AbstractIndependentBuilder`, `AbstractSealPileBuilder`) bind `Self` to a concrete builder and implement `self` (returning `this`), so the recursion bottoms out there.

## Members (delta over the javadoc)

- `Self self` — the abstract CRTP hook; the only method with no default. Implementations return `this`.
- `default Self configure(Consumer<? super Self> presets)` — hands `self` to a consumer and returns it. Convenience for packaging a bundle of builder calls (a "preset") and applying it inline.
- `V build` — finish construction and return the built value. Abstract; the abstract bases carry the real wiring.
- `V valueBeingBuilt` — the value object **as it exists mid-build**, before `build` finishes. Used by other default methods (and by `*_f` listener/bracket variants on sub-interfaces) to bring the value itself into scope during configuration. Abstract.
- `default Self runIfWeak(Runnable run)` — registers a [`WeakCleanup`](../utils/...) callback fired once the value being built becomes weakly reachable. **Gotcha:** the `Runnable` must hold no strong reference to the value (that would pin it and leak); any weak references it holds are cleared before it runs.
- `Self deferListeners(boolean b)` — abstract; toggle whether the built value defers listener notifications. `deferListeners` / `dontDeferListeners` are defaults delegating with `true` / `false`.

## Override map

- `self`, `build`, `valueBeingBuilt` — implemented in the abstract bases / concrete builders (not in this interface).
- `deferListeners(boolean)` — implemented identically in `AbstractPileBuilder` and `AbstractIndependentBuilder`: both just call `value._setDeferringListeners(b)`. No other overrides.
- `configure`, `runIfWeak`, `deferListeners`, `dontDeferListeners` — used as-is from the defaults; no overrides found.

## Caveats & gotchas

- **`name` / `owner` / `brackets` are NOT on `IBuilder`.** Despite the builder index's one-liner, the common-config methods (`name`, `nameIfUnnamed`, `bracket`, `owner`) are declared on the **target** interfaces (`IPileBuilder.java` for `name`; `bracket` on both `IPileBuilder` and `IIndependentBuilder`) and implemented in the abstract bases. `IBuilder` itself contributes only `self`/`configure`/`build`/`valueBeingBuilt`/`runIfWeak`/`deferListeners`. Treat "shared config" as a property of the abstract bases, not of this interface.
- `runIfWeak`'s memory-leak caveat is real and easy to trip: capturing the value (even transitively, e.g. via an inner class on a method that touches it) in the cleanup `Runnable` defeats the whole point.
- `valueBeingBuilt` returns a not-yet-finished value; reading/setting it directly during configuration bypasses the build-time wiring (e.g. `Independent.set` is deliberately deferred to `build` time so brackets/corrections apply — see `IIndependentBuilder`).

## Tech debt / warts

- The builder `_index.md` mis-attributes `name`/`owner` to `IBuilder`; see caveat above. (Doc, not code.)
- The CRTP self-type makes signatures verbose (`<Self extends IBuilder<Self, V>, V>` propagated through every sub-interface) — the standard cost of Java's lack of a `Self` type.

## Common tasks

- **Apply a reusable preset:** `builder.configure(b -> b.name("foo").seal)`.
- **Run cleanup when the built value is collected:** `builder.runIfWeak( -> …)` — no strong ref to the value inside the runnable.
- **Defer listener notifications on the built value:** `builder.deferListeners` (or `.deferListeners(false)` / `.dontDeferListeners`).
