# `pile.specialized_Comparable.combinations` — ordered-element combination interfaces (Tier 1 map)

Ten `*Comparable` interfaces that mirror ten of the twelve generic [`pile.aspect.combinations`](../../aspect/combinations/_index.md) interfaces, narrowed to `E extends Comparable<? super E>`, and adding an ordering surface (comparison operators, `readOnly`, `overridable`).

Source folder: `src` (all interfaces below).

Up: [Comparable package index](../_index.md) · [overview](../../../overview.md). Capstone concrete value: [`PileComparable`](../PileComparable.md). Generic counterparts: [aspect combinations index](../../aspect/combinations/_index.md). Bool sibling family: [specialized_bool combinations](../../specialized_bool/combinations/_index.md).

## What these interfaces are

Each `*Comparable` interface is a **thin assembly interface**: it narrows its generic counterpart to `E extends Comparable<? super E>`. The only one that adds substantive new API is [`ReadDependencyComparable`](ReadDependencyComparable.md) — it contributes the full ordering-operator surface (comparisons, `readOnly`, `overridable`) as default methods that delegate to static factories on [`PileBool`](../../specialized_bool/PileBool.md) and [`PileInt`](../../specialized_int/PileInt.md). All other nine interfaces in this family are **pure-assembly**: no new methods beyond the typed overrides of `setNull()` (which returns `this` narrowed to the concrete type).

The 4-dimension lattice (Read / Write / Listen / Dependency) and the capstone role of `Pile` are explained in the [generic combinations index](../../aspect/combinations/_index.md); the same shape holds here, with `PileComparable` as the capstone.

## Why only 10 interfaces (not 12)

The bool family has 12 `*Bool` combination interfaces; this family has **10**. Two are deliberately absent:

- **No `LastValueRemembererComparable`.** `LastValueRememberer` is element-type-agnostic plumbing (last-value memory for the change-listener bridge). An `E extends Comparable`-typed narrowing adds nothing useful — callers that need it use the generic `pile.aspect.LastValueRememberer` directly.
- **No `WriteElsewhereComparable`.** The `WriteElsewhere*` idea is dead in the bool family too (entirely commented out there as `WriteElsewhereBool`); there is no point specializing a dead feature.

## Map: `*Comparable` → generic counterpart

| `*Comparable` interface | Mirrors (generic) | Narrows / adds |
|---|---|---|
| [`JustReadValueComparable`](JustReadValueComparable.md) | [`JustReadValue`](../../aspect/JustReadValue.md) (+ `ReadValueComparable`) | Pure narrowing; `@FunctionalInterface`-equivalent entry point. No new members. |
| [`ReadValueComparable`](ReadValueComparable.md) | [`ReadValue`](../../aspect/ReadValue.md) | Pure narrowing to `E extends Comparable`. No new members. |
| [`ReadDependencyComparable`](ReadDependencyComparable.md) | [`ReadDependency`](../../aspect/combinations/ReadDependency.md) | **Adds the full ordering surface**: `lessThan`/`greaterThan`/`lessThanOrEqual`/`greaterThanOrEqual` (+ `*Const` variants) → [`SealBool`](../../specialized_bool/SealBool.md); `compareTo`/`compareToConst` → [`SealInt`](../../specialized_int/SealInt.md); `readOnly()` → `SealComparable`; `overridable()` → `PileComparableImpl`. |
| [`ReadListenValueComparable`](ReadListenValueComparable.md) | [`ReadListenValue`](../../aspect/combinations/ReadListenValue.md) | Pure narrowing. No new members. |
| [`ReadListenDependencyComparable`](ReadListenDependencyComparable.md) | [`ReadListenDependency`](../../aspect/combinations/ReadListenDependency.md) | Pure narrowing (inherits ordering ops from `ReadDependencyComparable`). No new members. |
| [`WriteValueComparable`](WriteValueComparable.md) | [`WriteValue`](../../aspect/WriteValue.md) | **Adds** typed `setNull()` → returns `WriteValueComparable<E>`. |
| [`ReadWriteValueComparable`](ReadWriteValueComparable.md) | [`ReadWriteValue`](../../aspect/combinations/ReadWriteValue.md) | **Adds** typed `setNull()` → returns `ReadWriteValueComparable<E>`. |
| [`ReadWriteDependencyComparable`](ReadWriteDependencyComparable.md) | [`ReadWriteDependency`](../../aspect/combinations/ReadWriteDependency.md) | **Adds** typed `setNull()` → returns `ReadWriteDependencyComparable<E>`. |
| [`ReadWriteListenValueComparable`](ReadWriteListenValueComparable.md) | [`ReadWriteListenValue`](../../aspect/combinations/ReadWriteListenValue.md) | **Adds** typed `setNull()` → returns `ReadWriteListenValueComparable<E>`. |
| [`ReadWriteListenDependencyComparable`](ReadWriteListenDependencyComparable.md) | [`ReadWriteListenDependency`](../../aspect/combinations/ReadWriteListenDependency.md) | **Adds** typed `setNull()` → returns `ReadWriteListenDependencyComparable<E>`. |

`PileComparable` (the capstone, in the parent package) extends `ReadWriteListenDependencyComparable` and adds recompute/transaction/transform/seal — exactly as generic `Pile` sits atop `ReadWriteListenDependency`.

## The ordering surface (what's callable and where)

The entire ordering-operator surface is declared on `ReadDependencyComparable` and therefore available on every subtype down to `PileComparable`. It is absent from `ReadValueComparable` and `ReadListenValueComparable` (which are not `Dependency` subtypes).

### Comparison → reactive `SealBool` (delegates to `PileBool`)

Each operator has a **reactive-operand** form (`op2` is a `ReadDependency<? extends E>`) and a **`*Const`** form (`op2` is a plain `E`). All take a `Boolean nullIsLess` three-valued null-ordering policy (see the parent index for the convention). All produce a fresh [`SealBool`](../../specialized_bool/SealBool.md):

- `lessThan(op2, nullIsLess)` / `lessThanConst(op2, nullIsLess)` — delegates to `PileBool.lessThan`
- `greaterThan(op2, nullIsLess)` / `greaterThanConst(op2, nullIsLess)` — delegates to `PileBool.greaterThan`
- `lessThanOrEqual(op2, nullIsLess)` / `lessThanOrEqualConst(op2, nullIsLess)` — delegates to `PileBool.lessThanOrEqual`
- `greaterThanOrEqual(op2, nullIsLess)` / `greaterThanOrEqualConst(op2, nullIsLess)` — delegates to `PileBool.greaterThanOrEqual`

### Comparison → reactive `SealInt` (delegates to `PileInt`)

- `compareTo(op2, nullIsLess)` / `compareToConst(op2, nullIsLess)` — delegates to `PileInt.comparison`; the reactive analogue of `Comparable.compareTo` returning an `int`.

### Wrappers

- `readOnly()` — wraps `this` in a [`SealComparable`](../SealComparable.md) read-only wrapper via `Piles.makeReadOnlyWrapper`.
- `overridable()` — builds a `PileComparableImpl` that recomputes from `this::get` and fires when this changes; useful for giving a read-only dependency a settable override layer.

## Caveats & gotchas

- **Ordering ops are on `ReadDependencyComparable`, not on `ReadValueComparable`.** A value typed as `ReadValueComparable` does not expose `lessThan` etc. — you need at least `ReadDependencyComparable`.
- **Comparison results are in a different family.** `lessThan(...)` yields a [`SealBool`](../../specialized_bool/SealBool.md); `compareTo(...)` yields a [`SealInt`](../../specialized_int/SealInt.md). To keep chaining you cross into the bool or int specialized families.
- **`setNull()` chain return types.** Each assembly interface overrides `setNull()` to return its own type, enabling fluent chaining without a cast. The override is purely a type narrowing — the body is always `set(null); return this`.
- **No write-side ordering operators.** Unlike `ReadWriteDependencyBool` (which adds `notRW`/`invertIf`), the `ReadWriteDependency*` comparable interfaces add nothing beyond the typed `setNull()`. There is no write-through comparison operation.
- **`null` propagates as a reactive `null`.** With `nullIsLess = null` (the `Boolean`), any `null` operand makes the result reactive-`null` (i.e. invalid/unknown) rather than forcing a total order. Auto-unboxing of `Boolean nullIsLess` arguments can cause `NullPointerException` if a caller passes `null` and an implementation tries to use it as `boolean` — pass `null` only where the three-valued overload is expected.
