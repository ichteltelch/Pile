# `pile.specialized_Comparable.SealComparable`

`SealComparable<E>` is `SealPile<E>` narrowed to `E extends Comparable<? super E>`, additionally implementing `PileComparable<E>` — the sealable ordered value and the type that `ReadDependencyComparable.readOnly()` / comparison factories hand back.

Source folder: `src`. File: `pile/specialized_Comparable/SealComparable.java`.

Up: [Comparable index](_index.md) · [overview](../../overview.md). Generic base: [`../impl/SealPile.md`](../impl/SealPile.md). The `specialized_String` family's `SealString` follows the same pattern.

## What it specializes

`SealComparable<E> extends SealPile<E> implements ReadWriteListenDependencyComparable<E>, PileComparable<E>`. All reactive semantics — sealing, write-back redirection, the validity/transaction machinery — are entirely inherited from [`SealPile`](../impl/SealPile.md). Read that doc for the full behavior contract.

## Added / narrowed members

| Member | Delta |
|---|---|
| `setName(String)` | Returns `SealComparable<E>` (covariant). Sets `avName = name` directly (matches the field-assignment pattern used in `SealPile`). |
| `setNull()` | Returns `SealComparable<E>` (covariant). Delegates to `set(null)`. |

`SealComparable` implements both `ReadWriteListenDependencyComparable` and `PileComparable`, so it carries both the ordering operators from `ReadDependencyComparable` and the full `Pile`-level contract (recomputation, `Recomputer`, depender registration). It is the node type produced by every ordering-operator factory on `ReadDependencyComparable` (`lessThan`, `greaterThan`, etc. produce a `SealBool`; `compareTo` produces a `SealInt`; `readOnly()` produces a `SealComparable<E>` wrapping the original) and by the static `PileComparable.compareTo` factory.

## Role in the factory chain

- `ReadDependencyComparable.readOnly()` calls `Piles.makeReadOnlyWrapper(this, new SealComparable<>())` → returns a `SealComparable<E>` sealed to the source.
- `ReadDependencyComparable.overridable()` returns a `PileComparableImpl<E>` (not a `SealComparable`).
- Static comparison factories in `PileBool` (`lessThan`, `greaterThan`, …) use `SealBool`; `PileInt.comparison` uses `SealInt`; `PileComparable.compareTo` uses `SealInt` — so `SealComparable` itself is only directly produced by `readOnly()` and by user-assembled `PileBuilder` / `Piles.makeBinOp` patterns.

## Caveats & gotchas

- `SealComparable` is the base of `SealString` in the `specialized_String` family — the pattern repeats.
- Write-back semantics (seal redirection) are fully inherited from `SealPile`; `setNull()` follows the same redirect path as `set(E)`.
- Because it implements `PileComparable`, it participates in the full `Pile` contract (supports a `Recomputer`, can be a `Depender`), even when used as a simple read-only wrapper.

## Related

- [`../impl/SealPile.md`](../impl/SealPile.md) — full behavior contract (sealing, write-back, validity).
- [`_index.md`](_index.md) — the `*Comparable` specialization pattern.
- [`combinations/_index.md`](combinations/_index.md) — `ReadDependencyComparable` (ordering operators; `readOnly()` → `SealComparable`, `overridable()` → `PileComparableImpl`).
- [`../impl/Piles/_index.md`](../impl/Piles/_index.md) — `Piles.min` / `Piles.max` which return `SealPile<E>` (the generic base).
