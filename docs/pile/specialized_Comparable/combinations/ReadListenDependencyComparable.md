# `ReadListenDependencyComparable`

Pure-assembly narrowing of [`ReadListenDependency`](../../aspect/combinations/ReadListenDependency.md) to `E extends Comparable<? super E>`; inherits the full ordering surface from `ReadDependencyComparable`. No new members.

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`ReadListenDependencyComparable<E>` extends `ReadListenDependency<E>`, `ReadListenValueComparable<E>`, and `ReadDependencyComparable<E>`. It is the **readable, observable, dependable** rung of the comparable lattice — the position between `ReadDependencyComparable` and the full `PileComparable` capstone. No new methods are declared; all ordering operators (`lessThan`, `greaterThan`, `compareTo`, `readOnly`, `overridable`) are inherited from `ReadDependencyComparable`, and all observation/buffer machinery from `ReadListenDependency`.

## Notes

- This is the type returned by `readOnly()` on a `ReadWriteListenDependencyComparable` (after wrapping in `SealComparable`, which implements this). Check `SealComparable` for details.
- `ReadWriteListenDependencyComparable` and `PileComparable` both extend this.
