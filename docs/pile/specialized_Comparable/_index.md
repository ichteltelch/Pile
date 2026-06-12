# `pile.specialized_Comparable` — package index (Tier 1, delta)

The **ordered-element specialization family**: a parallel `*Comparable` hierarchy typed to `E extends Comparable<? super E>` that mirrors [`pile.specialized_bool`](../specialized_bool/_index.md) but trades the boolean *algebra* for an *ordering* surface (comparisons, min/max), and **drops the leaf box types**.

Source folder: `src` (all classes below).

Up: [overview](../../overview.md). Exemplar family: [`../specialized_bool/_index.md`](../specialized_bool/_index.md) — **read it first for the whole pattern** (generality in, specialization out; IS-A-the-generic-type inheritance; narrowed return types; memoized derived operators; silent-ignore/no-op idioms). Sibling numeric family: [`../specialized_int/_index.md`](../specialized_int/_index.md). Generic counterparts: [`../impl/PileImpl.md`](../impl/PileImpl.md), [`../aspect/_index.md`](../aspect/_index.md). Cross-type factories incl. min/max: [`../impl/Piles/_index.md`](../impl/Piles/_index.md).

This index records only the **deltas** from the bool exemplar. Inheritance structure, validity/transaction semantics, typed `setName`/`setNull`, and the no-op idioms are exactly as documented there.

## The generic bound

Every type in this family is parameterized `<E extends Comparable<? super E>>` (see `PileComparable`, `PileComparableImpl`, `SealComparable`, `ConstantComparable`, `IndependentComparable`, and every `*Comparable` combination interface). Unlike `bool`/`int`/`double`/`String` — which pin one concrete element type — this family stays **generic over any naturally-ordered `E`**. It is the "refined element type" arm of the pattern: a `PileComparable<LocalDate>`, `PileComparable<MyEnum>`, etc.

## Structural deltas vs. the bool exemplar

Three things the exemplar has that this family **deliberately omits**:

- **No `MutComparable`.** The `Mut*` bare mutable box (a closure/out-param, not a graph node) exists only for `bool`/`int`. An ordered domain has no use for a primitive-style scalar box.
- **No `SuppressComparable`.** The reference-counted suppression-flag value (`SuppressBool`/`SuppressInt`) is inherently boolean/counter-shaped; it has no `Comparable` analogue.
- **`combinations/` has 10 interfaces, not 12.** Missing relative to bool: **`LastValueRemembererComparable`** and **`WriteElsewhereComparable`**. Those two are element-type-agnostic plumbing (last-value memory; the now-inert write-elsewhere variant) that this family simply doesn't re-specialize — an ordered domain gains nothing from a typed flavour of them, so a consumer needing them uses the generic `pile.aspect.combinations` contracts directly. The 10 present are: `ReadValueComparable`, `ReadListenValueComparable`, `WriteValueComparable`, `ReadWriteValueComparable`, `ReadWriteListenValueComparable`, `JustReadValueComparable`, `ReadDependencyComparable`, `ReadListenDependencyComparable`, `ReadWriteDependencyComparable`, `ReadWriteListenDependencyComparable`.

## Concrete types — same 1:1 mapping, minus the leaves

| `*Comparable` type | extends (generic) | role |
|---|---|---|
| `PileComparableImpl` | [`PileImpl`](../impl/PileImpl.md) | default full reactive ordered value; implements `PileComparable`. Typed `setName`/`setNull` only — no operator memoization of its own (the ordering ops are static factories, see below). |
| `SealComparable` | `SealPile` | sealable ordered value; the type the `readOnly()` factory hands back. |
| `ConstantComparable` | `Constant` | never-changing ordered value; `setNull()` is a no-op returning `this` (constant), matching the silent-ignore idiom. |
| `IndependentComparable` | `Independent` | always-valid non-recomputing leaf; typed `setName`/`setNull`. |

There is **no** `PileComparable.java`-side memoized derived operator (contrast `IndependentBool.not`): unlike `not()`, the ordering operators are *binary* (two operands) and are produced as fresh `Seal*` nodes by static factories, so there is nothing per-instance to memoize.

## The Comparable-specific surface — ordering, not algebra

This is the heart of the delta. **The operator methods live on [`ReadDependencyComparable`](combinations/ReadDependencyComparable.md)** (so every readable ordered value has them), and **`PileComparable` itself adds almost nothing** — just `setNull()` plus a handful of static comparison *helpers*. Crucially, the operators are **thin delegators**: they route to the cross-type statics on `PileBool` / `PileInt` / `Piles`, and the *comparison results* are themselves reactive values of a different family.

### Comparison → reactive `bool` / `int` (on `ReadDependencyComparable`)

Each method has a reactive-operand form and a `*Const` form (compare against a fixed `E`), and every form takes a `Boolean nullIsLess` ordering choice for `null` (see null-ordering below). All produce a fresh sealable node:

- `lessThan` / `greaterThan` / `lessThanOrEqual` / `greaterThanOrEqual` (+ `lessThanConst` / `greaterThanConst` / `lessThanOrEqualConst` / `greaterThanOrEqualConst`) → **`SealBool`**, by delegating to the matching `PileBool.lessThan/greaterThan/lessThanOrEqual/greaterThanOrEqual` static.
- `compareTo(op2, nullIsLess)` / `compareToConst(constVal, nullIsLess)` → **`SealInt`** (the reactive analogue of `Comparable.compareTo`'s `int`), delegating to `PileBool.comparison` (named `comparison`, not `compareTo`, on the int/bool side via `PileInt.comparison`).

> Naming note: there are **no** `atLeast` / `atMost` / `eq` methods (those names appear nowhere in this family). The "≥ / ≤" operators are spelled `greaterThanOrEqual` / `lessThanOrEqual`; equality has no dedicated reactive operator here (use `compareTo == 0`, or the generic equality relations in `pile.relation`).

### Static helpers on `PileComparable`

`PileComparable` declares one static factory and three pure comparators:

- `compareTo(op1, op2, nullIsLess)` → `SealInt` — builds a reactive `int` comparison of two `ReadDependency`s via `Piles.makeBinOp`, choosing the comparator from `nullIsLess`.
- `compareNullIsNull` / `compareNullIsLess` / `compareNullIsGreater` — the three plain (non-reactive) `(a,b)->Integer/int` comparators that encode the null-ordering policy. `compareNullIsNull` returns a *boxed* `Integer` that is `null` when either operand is null; the other two return primitive `int`.

### min / max / clamp — **not here; on `Piles`**

There are **no `min` / `max` / `clamp` methods on `PileComparable` or `ReadDependencyComparable`** (and no `clamp` anywhere in the library). Reactive minimum/maximum of ordered reactive values is built with the **static factories on [`Piles`](../impl/Piles/_index.md)**: `Piles.min` / `Piles.max` (→ `SealPile<E>`), the lower-level `minOp` / `maxOp` (and `minOpNullIsLess` / `minOpNullIsGreater` / `maxOpNullIsLess` / `maxOpNullIsGreater`) binary combiners, and the `minAggregation` / `maxAggregation` (`…AggregationC` for a constant bound) monoid aggregators. Each accepts **either** a `Comparator<? super E>` **or** a `Boolean nullIsLess`. So the family's "ordering operators" are split: per-value *comparisons* on `ReadDependencyComparable`, but *selection* (min/max/aggregate) on `Piles`. There is no clamp; compose it from `Piles.min`+`Piles.max` if needed.

### Null-ordering choices (the `Boolean nullIsLess` convention)

Every comparison/min/max entry point threads a `Boolean nullIsLess` with **three-valued** meaning (mirrored by the three `PileComparable.compareNullIs*` comparators):

- `Boolean.TRUE` → `null` sorts **less** than every non-null (`compareNullIsLess`);
- `Boolean.FALSE` → `null` sorts **greater** than every non-null (`compareNullIsGreater`);
- `null` (the `Boolean` is itself null) → the comparison/result **is `null`** whenever an operand is null (`compareNullIsNull`) — i.e. "unknown ordering" propagates as an invalid/`null` reactive result rather than forcing a total order.

A `Comparator<? super E>` overload (on the `Piles`/`PileBool` side) bypasses natural ordering entirely.

## Companion docs

- [`combinations/_index.md`](combinations/_index.md) — the **10** `*Comparable` combination interfaces; `ReadDependencyComparable` is where the ordering operators (and `readOnly()` → `SealComparable`, `overridable()` → `PileComparableImpl`) are declared.

## Caveats & gotchas

- **Don't look for the operators on the value class.** `PileComparableImpl` is nearly empty; the comparison surface is inherited from `ReadDependencyComparable`, and selection (min/max) is on `Piles`. This split differs from `bool`, where `not()` and the algebra both hang off `PileBool`.
- **Comparison results are a different family.** `lessThan(...)` gives you a `SealBool`, `compareTo(...)` a `SealInt` — to keep chaining you cross into [`specialized_bool`](../specialized_bool/_index.md) / `specialized_int`.
- **Three-valued `nullIsLess`.** Passing a literal `false` (greater) vs. a `null` `Boolean` (result becomes `null`) are very different; the auto(un)boxing makes the `null` case easy to trigger accidentally.
- **No `Mut`/`Suppress`/`clamp`/`eq`/`atLeast`/`atMost`** — see above; these are absences by design, not omissions to fix.
</content>
</invoke>
