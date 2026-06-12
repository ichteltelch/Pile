# `pile.specialized_Comparable.PileComparable`

`PileComparable<E>` is the capstone interface of the ordered-value family: `Pile<E>` + `ReadWriteListenDependencyComparable<E>` + `Depender`, adding a `setNull()` default and three static pure comparators plus one static reactive factory.

Source folder: `src`. File: `pile/specialized_Comparable/PileComparable.java`.

Up: [Comparable index](_index.md) · [overview](../../overview.md). The comparison→bool/int operators are on [`combinations/ReadDependencyComparable`](combinations/_index.md), not here. Min/max selection: [`../impl/Piles/_index.md`](../impl/Piles/_index.md).

## What it is

`PileComparable<E extends Comparable<? super E>> extends Depender, ReadWriteListenDependencyComparable<E>, Pile<E>`. It is the ordered-value analogue of `PileBool` / `PileInt` — the top-level typed interface that the concrete implementations (`PileComparableImpl`, `SealComparable`) implement. Unlike `PileBool`, it carries **almost no instance-side abstract methods**: the ordering operators are on `ReadDependencyComparable` (lower in the combination hierarchy); `PileComparable` itself is nearly a pure marker plus a small statics annex.

## Instance-side members

| Member | Delta |
|---|---|
| `setNull()` | `default` implementation: `set(null); return this`. Concrete subclasses narrow the return type covariantly (`PileComparableImpl`, `SealComparable`). |

No other abstract instance methods are declared.

## Static members — the comparator annex

`PileComparable` acts as a namespace for the three null-ordering comparators and one reactive factory that the whole family's comparison machinery is built on.

### `compareTo(op1, op2, nullIsLess)` → `SealInt`

Builds a reactive `int`-valued comparison of two `ReadDependency<? extends S>` operands via `Piles.makeBinOp`. The `Boolean nullIsLess` parameter picks the comparator (see null-ordering below). This is the **instance-free** version of `ReadDependencyComparable.compareTo`; use the instance method when one operand is already a `*Comparable` value.

Note: `ReadDependencyComparable.compareTo` delegates to `PileInt.comparison`, not to this static — they are parallel routes to the same result.

### Null-ordering comparators

Three pure (non-reactive) `BiFunction`/`BiPredicate`-compatible static methods encoding the three null-ordering policies used throughout the family:

| Method | Returns | `null` treatment |
|---|---|---|
| `compareNullIsNull(a, b)` | `Integer` (boxed, nullable) | Either operand `null` → return `null`; models "unknown ordering" |
| `compareNullIsLess(a, b)` | `int` (primitive) | `null` sorts **before** all non-null |
| `compareNullIsGreater(a, b)` | `int` (primitive) | `null` sorts **after** all non-null |

These are the canonical comparators that all ordering operators and `Piles.min`/`Piles.max` build on when given the three-valued `Boolean nullIsLess` parameter. `compareNullIsNull` returns a boxed `Integer` (nullable) because its result itself may be `null`; the other two return primitive `int`.

## The ordering operator surface — NOT here

The comparison operators (`lessThan`, `greaterThan`, `lessThanOrEqual`, `greaterThanOrEqual`, `compareTo(op2, nullIsLess)`, and their `*Const` variants) live on `ReadDependencyComparable` (in `combinations/`), so they are available on **every readable ordered value** regardless of whether it is a `PileComparable`. See [`combinations/_index.md`](combinations/_index.md) for the full operator list and cross-type delegation targets (`PileBool.*` for `→SealBool` results, `PileInt.comparison` for `→SealInt` results).

## Min / max — not here

No `min` / `max` / `clamp` methods exist on this interface. Reactive min/max is via `Piles.min` / `Piles.max`; see [`../impl/Piles/_index.md`](../impl/Piles/_index.md).

## Caveats & gotchas

- `compareNullIsNull` returns a nullable `Integer`, not `int` — auto-unboxing the result throws if both inputs were non-null but one was later set to `null` during a reactive recompute. In a reactive context always use it as a `BiFunction<S, S, Integer>`, not an `IntBinaryOperator`.
- The `Boolean nullIsLess` convention is **three-valued** (`TRUE` / `FALSE` / `null`). Passing a literal `false` (greater) vs. a `null` `Boolean` (propagate-null) are very different semantics; Java autoboxing makes the `null` case easy to trigger by accident when passing a variable of type `boolean`.
- `PileComparable` itself carries very little; don't look here for the operator surface — that is `ReadDependencyComparable`.

## Related

- [`_index.md`](_index.md) — the `*Comparable` family overview and delta from `specialized_bool`.
- [`combinations/_index.md`](combinations/_index.md) — `ReadDependencyComparable` (all ordering operators) and the full combination hierarchy.
- [`PileComparableImpl.md`](PileComparableImpl.md) — the primary concrete implementation.
- [`SealComparable.md`](SealComparable.md) — the sealable concrete implementation, also implementing this interface.
- [`../specialized_bool/_index.md`](../specialized_bool/_index.md) — `SealBool`, produced by the ordering-operator factories.
- [`../specialized_int/_index.md`](../specialized_int/_index.md) — `SealInt`, produced by `compareTo` operators.
- [`../impl/Piles/_index.md`](../impl/Piles/_index.md) — `Piles.min` / `Piles.max` and the null-ordering overloads.
