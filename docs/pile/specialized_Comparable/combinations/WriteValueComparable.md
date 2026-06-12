# `WriteValueComparable`

Narrows [`WriteValue`](../../aspect/WriteValue.md) to `E extends Comparable<? super E>` and adds a typed `setNull()`.

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`WriteValueComparable<E>` extends `WriteValue<E>`. It is the write-only root of the comparable lattice — a settable ordered value with no read, listen, or dependency capability. The one addition over the generic `WriteValue` is a typed `setNull()` override that returns `WriteValueComparable<E>` (enabling fluent chaining without a cast).

## `setNull()`

`setNull()` calls `set(null)` and returns `this`. The return type is narrowed to `WriteValueComparable<E>` so that callers holding this interface type can chain without an unchecked cast. Subtypes (`ReadWriteValueComparable`, `ReadWriteDependencyComparable`, etc.) further narrow the return type at each level.

## Notes

- No ordering operators — those require the read side (`ReadDependencyComparable`).
- No `setTrue`/`setFalse` equivalents; an ordered domain has no canonical boolean-style convenience writes.
