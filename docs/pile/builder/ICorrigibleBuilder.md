# `ICorrigibleBuilder`

The capability-builder interface that adds corrector/bounds/equivalence configuration to a fluent builder — `neverNull`, `corrector`, upper/lower `bounds`, `ordering`, and the static `applyBounds` wiring that compiles bounds into correctors plus `"min"`/`"max"` associations.

Source folder: `src` · package `pile.builder` · file `ICorrigibleBuilder.java`.

Up: [builder index](_index.md) · [root `IBuilder`](IBuilder.md) · [overview](../../overview.md). Aspect built: [`CorrigibleValue`](../aspect/CorrigibleValue.md). Bounds are stored via [`HasAssociations`](../aspect/HasAssociations.md).

## What it's for

`ICorrigibleBuilder<Self, V extends CorrigibleValue<E>, E>` is the common superinterface for builders that produce a [`CorrigibleValue`](../aspect/CorrigibleValue.md) — a value whose incoming writes pass through a corrector chain. It exists "mainly to have a common interface for putting bounds on the value range"; multiple bounds, if defined, are all enforced. It is a capability interface in the CRTP hierarchy (see [`IBuilder`](IBuilder.md) § the self-type pattern), inherited — not used directly — by the target builders.

## Who inherits it

- [`IPileBuilder`](IPileBuilder.md) extends `ICorrigibleBuilder`.
- [`IIndependentBuilder`](IIndependentBuilder.md) extends `ICorrigibleBuilder` (+ `IListenValueBuilder` + `ISealableBuilder`).

The actual implementations live in the abstract bases: [`AbstractPileBuilder`](AbstractPileBuilder.md) and [`AbstractIndependentBuilder`](AbstractIndependentBuilder.md) both call `ICorrigibleBuilder.applyBounds(...)` from their `build`. So every concrete Pile/Independent/SealPile builder transitively gets this API.

## Members (delta over the javadoc)

The per-method contracts are in the javadoc. Notable points:

- **Corrector installers** — `corrector(Function)` appends one corrector step immediately via the value's `_addCorrector` (it "is added immediately and will influence future attempts to change the value"). `neverNull` is a *default* that installs a pure-veto corrector throwing `VetoException("This value may not be set to null!")` on `null`. Both ultimately feed [`CorrigibleValue._addCorrector`](../aspect/CorrigibleValue.md) — correctors run in insertion order; see that doc for the chain semantics.
- **Bound setters** — `upperBound`/`lowerBound` come in a constant overload (wrapping the bound in a [`Constant`](../impl/Constant.md), ,) and a variable overload taking a `ReadListenDependency` (,, abstract — implemented in the abstract bases, which *collect* bounds into lists rather than applying them eagerly). The variable forms are the primitives; everything else delegates.
- **`bounds(...)`** — four `default` overloads (every constant/variable combination of lower+upper) that just chain `lowerBound(lower).upperBound(upper)`.
- **`unorderedBounds(bound1, bound2)`** — `default` that uses `getOrdering` to decide which argument is the lower and which the upper, then sets both. **Requires an ordering to have been defined first** (`ordering(...)`), or `getOrdering` returns `null` and this NPEs.
- **`ordering(Comparator)` / `orderingRaw(Comparator)`** — define the comparator used to compare value vs. bound during correction; each call overwrites the previous ordering. `ordering` makes the correction throw a `VetoException` on a `null` value or bound; `orderingRaw` requires the comparator to handle `null` itself. `getOrdering` reads back the current one.
- **`equivalence(BiPredicate)`** — defines the equivalence relation used to decide whether the value actually changed. Abstract; implemented in the abstract bases. (`IPileBuilder` documents its own equivalence too — same method, inherited here.)
- **`valueBeingBuilt`** — re-declared from [`IBuilder`](IBuilder.md); the mid-build value object.

## The static bound machinery (`applyBounds` and friends)

`ICorrigibleBuilder` carries the **static** helpers that turn collected bounds into installed behavior — these are what the abstract bases call in `build`:

- **`applyBounds(val, lowerBounds, upperBounds, ordering)`** compiles the bound lists and installs up to three correctors on `val`:
  1. If there are upper bounds: aggregate multiple via `Piles.minAggregation` (a single bound is used directly), then add a clamp corrector that returns the bound when `ordering.compare(value, bound) > 0`. Stores the upper bound association.
  2. Symmetrically for lower bounds via `Piles.maxAggregation`, clamping up.
  3. If both exist: a consistency corrector that **vetoes** when `lower > upper`.
  Each corrector reads its bound through `getValidOrThrow`; if the bound is currently invalid it throws `VetoException("…bound not valid", e)` — so a write can be vetoed purely because a *bound* value is momentarily invalid. **Requires an ordering** if any bound is present, else `IllegalStateException`.
- **`putUpperBound` / `putLowerBound`** — store a bound under the association key; **do not** touch correctors/listeners/dependencies (the comment says as much, ,). `applyBounds` calls these; calling them yourself only records the association without enforcement.
- **`getUpperBound` / `getLowerBound`** — read the bound association back; `null` if none.

### The bound association keys

The keys live on [`AbstractPileBuilder`](AbstractPileBuilder.md), not here:
`protected static NamedAssociationKey<?> upperBound = new NamedAssociationKey<>("max")` and `lowerBound = new NamedAssociationKey<>("min")`. So a value's bounds are queryable on the built value as the `"max"`/`"min"` associations via [`HasAssociations`](../aspect/HasAssociations.md). The `applyBounds`/`putXBound`/`getXBound` statics cast these untyped keys with `@SuppressWarnings("unchecked")`.

## Salient / surprising behavior

- **Bounds become dependencies of the value.** `applyBounds` only installs correctors + associations; the abstract base separately calls `value.addDependency(ub/lb)` right after, using `getUpperBound`/`getLowerBound` to retrieve them. So enforcement (corrector) and reactivity (dependency edge) are wired in two different places.
- **Clamping vs. veto.** Out-of-range writes are *clamped* (corrector returns the bound), not rejected — but an *invalid bound* or an *inconsistent* lower>upper pair is a *veto*. See [`CorrigibleValue` § normalize vs. veto](../aspect/CorrigibleValue.md).
- **Order of configuration matters for `unorderedBounds`**: it consults `getOrdering` at call time, so set `ordering(...)` first.

## Caveats & gotchas

- `unorderedBounds` and `applyBounds` both hard-depend on an ordering; forgetting `ordering(...)` yields an NPE (`unorderedBounds`) or `IllegalStateException` (`applyBounds`).
- The bound keys are `NamedAssociationKey<?>` (raw wildcard) and accessed through unchecked casts — type safety of the stored bound is by convention only.
- Correctors are append-only and cannot be removed (a property of [`CorrigibleValue`](../aspect/CorrigibleValue.md), not fixable from the builder).
- `corrector(...)` installs *immediately* (it mutates `valueBeingBuilt`), whereas bounds are *deferred* (collected into lists, applied in `build`). Mixing a manual `corrector` with `bounds` therefore does not give a predictable interleaving relative to the bound correctors — the bound correctors are all appended at `build` time, after any eager `corrector(...)` calls.

## Common tasks (how to…)

- **Clamp into a range:** `builder.ordering(cmp).bounds(lo, hi)` (constant or `ReadListenDependency` bounds). Multiple lower/upper bounds aggregate (max of lowers, min of uppers).
- **Two bounds without knowing their order:** `builder.ordering(cmp).unorderedBounds(a, b)`.
- **Reject `null`:** `builder.neverNull`.
- **Custom normalization:** `builder.corrector(v -> normalize(v))`.
- **Read a built value's bounds:** `ICorrigibleBuilder.getUpperBound(value)` / `getLowerBound(value)` (or the `"max"`/`"min"` associations).

## Tech debt / warts

- `applyBounds`'s type parameter `V extends HasAssociations & CorrigibleValue<E>` is **declared but unused** — the method takes `CorrigibleValue<E> val` and casts to `HasAssociations` internally. The `<V>` bound is dead; the cast is what actually enforces it.
- Bound association keys are wildcard-typed and reached via unchecked casts.
- The two halves of bound wiring (correctors in `applyBounds`, dependency edges in the abstract base) being split across files makes "what does adding a bound do" non-local.
