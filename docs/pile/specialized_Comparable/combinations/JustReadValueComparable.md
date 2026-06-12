# `JustReadValueComparable`

Pure-assembly narrowing of [`JustReadValue`](../../aspect/JustReadValue.md) to `E extends Comparable<? super E>`; no new members.

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`JustReadValueComparable<E>` extends `ReadValueComparable<E>` and `JustReadValue<E>`. It serves as the `@FunctionalInterface`-equivalent entry point for a read-only ordered value with no dependency tracking — a supplier that happens to supply a `Comparable`. It adds no methods of its own; the generic `JustReadValue` contract applies in full.

## Notes

- Does **not** extend `ReadDependencyComparable`, so the ordering operators (`lessThan`, `compareTo`, etc.) are **not** available on this type. To use ordering operators, the value must be at least `ReadDependencyComparable`.
- The non-dependency read types (`JustReadValue*`, `ReadValue*`, `ReadListenValue*`) exist mainly as parameter types for factory methods and builder overloads that accept any readable source without requiring graph-node status.
