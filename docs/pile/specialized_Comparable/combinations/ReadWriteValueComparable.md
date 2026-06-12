# `ReadWriteValueComparable`

Narrows [`ReadWriteValue`](../../aspect/combinations/ReadWriteValue.md) to `E extends Comparable<? super E>` and overrides `setNull()` with a typed return.

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`ReadWriteValueComparable<E>` extends `ReadValueComparable<E>`, `WriteValueComparable<E>`, and `ReadWriteValue<E>`. It is the read+write (non-observable, non-dependency) node of the comparable lattice. The only addition over the generic `ReadWriteValue` is a typed `setNull()` override returning `ReadWriteValueComparable<E>`.

## Notes

- **No ordering operators.** `ReadWriteValue` does not extend `Dependency`, so `lessThan` / `compareTo` etc. are not available. For ordering, use `ReadWriteDependencyComparable` or wider.
- `setNull()` narrows the return type at this level; `ReadWriteDependencyComparable` and the listen-bearing variants narrow it further.
