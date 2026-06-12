# `ReadWriteListenValueComparable`

Narrows [`ReadWriteListenValue`](../../aspect/combinations/ReadWriteListenValue.md) to `E extends Comparable<? super E>`; adds typed `setNull()`. No ordering operators (not a `Dependency`).

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`ReadWriteListenValueComparable<E>` extends `ReadListenValueComparable<E>`, `ReadWriteValueComparable<E>`, and `ReadWriteListenValue<E>`. It is the read + write + listen (non-dependency) rung — a writable ordered value that can be observed but is not itself a dependency node. The only new declaration is a typed `setNull()` override returning `ReadWriteListenValueComparable<E>`.

## Notes

- **No ordering operators.** This type does not extend `ReadDependencyComparable` or `Dependency`. For ordering operators, use `ReadWriteDependencyComparable` or `ReadWriteListenDependencyComparable`.
- `ReadWriteListenDependencyComparable` extends this (adding dependency status and, via `ReadDependencyComparable`, the ordering surface).
