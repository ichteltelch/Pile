# `pile.specialized_Comparable.ConstantComparable`

`ConstantComparable<E>` is `Constant<E>` narrowed to `E extends Comparable<? super E>`, adding the typed ordering surface — the thinnest possible specialization.

Source folder: `src`. File: `pile/specialized_Comparable/ConstantComparable.java`.

Up: [Comparable index](_index.md) · [overview](../../overview.md). Generic base: [`../impl/Constant.md`](../impl/Constant.md).

## What it specializes

`ConstantComparable<E> extends Constant<E> implements ReadWriteListenDependencyComparable<E>`. All reactive semantics — never-changing, always valid, silent writes, no listeners — are entirely inherited from [`Constant`](../impl/Constant.md). Read that doc for the full behavior contract.

## Added / narrowed members

| Member | Delta |
|---|---|
| `setNull()` | Returns `ConstantComparable<E>` (covariant). Silent no-op (`return this`), matching the `Constant` silent-ignore idiom. |

There is **no `setName` override** — the covariant return from `Constant.setName` is not narrowed here (unlike `IndependentComparable` and `SealComparable`). Writing a name has no effect regardless; the absence of the override is cosmetic.

The ordering operators (`lessThan`, `greaterThan`, `compareTo`, …) are fully inherited as `default` methods from `ReadDependencyComparable` — they build fresh reactive `SealBool`/`SealInt` nodes that recompute against this constant. They work correctly; comparing a constant against another value produces a constant-input reactive node (which simply stays fixed).

## Caveats & gotchas

- All writes (`set`, `setNull`) are **silently ignored**, as with the generic `Constant`.
- There is **no** constant-valued short-circuit for the ordering operators analogous to `ConstantBool.not()` — `lessThan(...)` on a constant creates a live reactive node (a `SealBool`) that recomputes when the *other* operand changes. The result is correct but carries unnecessary overhead if both operands are constant.
- `ConstantComparable` carries no memoized derived values of any kind (contrast `ConstantBool.not()`), consistent with the family's decision to make ordering operators binary static factories.

## Related

- [`../impl/Constant.md`](../impl/Constant.md) — full behavior contract (silent writes, always valid, no listeners).
- [`_index.md`](_index.md) — the specialization pattern and the whole `*Comparable` family.
- [`combinations/_index.md`](combinations/_index.md) — `ReadWriteListenDependencyComparable` and the `ReadDependencyComparable` ordering operators.
- [`../impl/Piles/_index.md`](../impl/Piles/_index.md) — `Piles.min` / `Piles.max` for selection over ordered values.
