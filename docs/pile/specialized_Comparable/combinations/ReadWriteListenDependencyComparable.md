# `ReadWriteListenDependencyComparable`

The full non-recompute `*Comparable` contract: narrows [`ReadWriteListenDependency`](../../aspect/combinations/ReadWriteListenDependency.md) to `E extends Comparable<? super E>`, inheriting all ordering operators; adds typed `setNull()`.

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`ReadWriteListenDependencyComparable<E>` extends `ReadWriteListenValueComparable<E>`, `ReadListenDependencyComparable<E>`, `ReadWriteDependencyComparable<E>`, and `ReadWriteListenDependency<E>`. It is the topmost combination interface in this family — the full read + write + listen + dependency contract for an ordered reactive value, without recompute/transaction/seal. `PileComparable` extends this and adds those final capabilities.

The only new declaration is a typed `setNull()` override returning `ReadWriteListenDependencyComparable<E>`.

All ordering operators are inherited from `ReadDependencyComparable` — see [ReadDependencyComparable.md](ReadDependencyComparable.md) for the full operator table.

## Notes

- `PileComparable` (the concrete capstone) extends this and is the type most application code will hold.
- The `setNull()` chain of typed overrides terminates here; `PileComparable` itself may narrow it further to `PileComparable<E>`.
- There is no `fallback`/`whileTrueRepeat` equivalent here (compare `ReadListenDependencyBool`): those are boolean-domain features with no ordered-element analogue.
