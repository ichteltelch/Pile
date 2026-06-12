# `pile.specialized_Comparable.PileComparableImpl`

`PileComparableImpl<E>` is `PileImpl<E>` narrowed to `E extends Comparable<? super E>` via `PileComparable<E>` — the default full reactive ordered value with typed `setNull`/`setName` and no additional logic.

Source folder: `src`. File: `pile/specialized_Comparable/PileComparableImpl.java`.

Up: [Comparable index](_index.md) · [overview](../../overview.md). Generic base: [`../impl/PileImpl.md`](../impl/PileImpl.md). Capstone interface: [`PileComparable.md`](PileComparable.md).

## What it specializes

`PileComparableImpl<E> extends PileImpl<E> implements PileComparable<E>`. All reactive semantics — dependency tracking, recomputation, validity, transactions, bracket-based listeners — are entirely inherited from [`PileImpl`](../impl/PileImpl.md). Read that doc for the full behavior contract.

## Added / narrowed members

| Member | Delta |
|---|---|
| `setName(String)` | Returns `PileComparableImpl<E>` (covariant). Sets `avName = name` directly (same field-assignment pattern as `SealComparable`). |
| `setNull()` | Returns `PileComparableImpl<E>` (covariant). Delegates to `set(null)`. |

That is the entire source body. `PileComparableImpl` is intentionally thin: it adds **no memoized derived operators** (contrast `PileBoolImpl.not()`). The ordering operators are all `default` methods on `ReadDependencyComparable`, inherited via `PileComparable`; each call builds a fresh reactive node. Min/max selection is on `Piles` statics, not here.

## Role in the factory chain

`ReadDependencyComparable.overridable()` produces a `PileComparableImpl<E>` configured to recompute from the source dependency. This is the "mutable override" variant of a `readOnly()` wrapper (which produces a `SealComparable`).

## Caveats & gotchas

- The type is **nearly empty** — all interesting behavior is in `PileImpl` (base) or `ReadDependencyComparable` (ordering operators via `PileComparable`). There is nothing to look for in `PileComparableImpl` beyond the typed return types.
- No operator memoization. Every `lessThan(…)` / `compareTo(…)` call creates a new reactive node. Cache the result if needed.

## Related

- [`../impl/PileImpl.md`](../impl/PileImpl.md) — full reactive behavior (recomputation, dependency tracking, validity, transactions).
- [`PileComparable.md`](PileComparable.md) — the interface this implements; adds `setNull()` default and the static comparison helpers/comparators.
- [`_index.md`](_index.md) — the `*Comparable` specialization pattern.
- [`combinations/_index.md`](combinations/_index.md) — `ReadDependencyComparable` where the ordering operators live.
- [`../impl/Piles/_index.md`](../impl/Piles/_index.md) — `Piles.min` / `Piles.max` for reactive min/max selection.
