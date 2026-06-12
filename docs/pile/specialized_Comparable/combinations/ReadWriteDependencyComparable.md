# `ReadWriteDependencyComparable`

Narrows [`ReadWriteDependency`](../../aspect/combinations/ReadWriteDependency.md) to `E extends Comparable<? super E>`, inheriting the full ordering surface; adds typed `setNull()`.

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`ReadWriteDependencyComparable<E>` extends `ReadWriteValueComparable<E>`, `ReadDependencyComparable<E>`, and `ReadWriteDependency<E>`. It is the **read + write + depend-on** (no listen) rung of the comparable lattice — a fully operable ordered reactive value without the change-listener/buffer machinery. The only new declaration is a typed `setNull()` override that returns `ReadWriteDependencyComparable<E>`.

All ordering operators (`lessThan`, `greaterThan`, `lessThanOrEqual`, `greaterThanOrEqual`, and their `*Const` variants; `compareTo`, `compareToConst`; `readOnly()`, `overridable()`) are inherited from `ReadDependencyComparable` — see [ReadDependencyComparable.md](ReadDependencyComparable.md) for the full operator table.

## Notes

- **No write-through ordering operators.** Unlike `ReadWriteDependencyBool` (which adds `notRW`/`invertIf`), this interface adds nothing on the write side beyond `setNull()`. There is no comparison-based write-back.
- `ReadWriteListenDependencyComparable` extends this and adds the listen/buffer machinery.
