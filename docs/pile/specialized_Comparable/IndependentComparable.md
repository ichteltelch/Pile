# `pile.specialized_Comparable.IndependentComparable`

`IndependentComparable<E>` is `Independent<E>` narrowed to `E extends Comparable<? super E>`, adding typed covariant returns for `setNull` and `setName` — no other delta.

Source folder: `src`. File: `pile/specialized_Comparable/IndependentComparable.java`.

Up: [Comparable index](_index.md) · [overview](../../overview.md). Generic base: [`../impl/Independent.md`](../impl/Independent.md).

## What it specializes

`IndependentComparable<E> extends Independent<E> implements ReadWriteListenDependencyComparable<E>`. All reactive semantics — always-valid, non-recomputing, direct-write leaf node, silent-ignore of `allowInvalidation` — are unchanged from [`Independent`](../impl/Independent.md). Read that doc for the full behavior contract.

## Added / narrowed members

| Member | Delta |
|---|---|
| `setName(String)` | Returns `IndependentComparable<E>` (covariant). Calls `super.setName(name)` then returns `this`. |
| `setNull()` | Returns `IndependentComparable<E>` (covariant). Implemented as `set(null); return this`. |

No operator memoization is added (contrast `IndependentBool.not()` which memoizes a derived constant-free node). The ordering operators on `ReadDependencyComparable` are binary factories and need no per-instance cache.

## Caveats & gotchas

- `allowInvalidation(…)` on an `Independent` is a no-op — `IndependentComparable` inherits this silent-ignore behavior; it is idiomatic, not a bug.
- Unlike `IndependentBool`, there are no memoized derived operators. Each call to `lessThan(…)` / `compareTo(…)` etc. builds a new fresh reactive node.
- The ordering operators are inherited from `ReadDependencyComparable` as `default` methods; they are available on every `IndependentComparable<E>` instance.

## Related

- [`../impl/Independent.md`](../impl/Independent.md) — full behavior contract (always valid, direct-write, no recomputation).
- [`_index.md`](_index.md) — the `*Comparable` specialization pattern.
- [`combinations/_index.md`](combinations/_index.md) — `ReadDependencyComparable` (ordering operators) and `ReadWriteListenDependencyComparable`.
- [`../impl/Piles/_index.md`](../impl/Piles/_index.md) — `Piles.min` / `Piles.max` for reactive min/max selection.
