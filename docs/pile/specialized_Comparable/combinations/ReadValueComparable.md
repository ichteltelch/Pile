# `ReadValueComparable`

Pure-assembly narrowing of [`ReadValue`](../../aspect/ReadValue.md) to `E extends Comparable<? super E>`; no new members.

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`ReadValueComparable<E>` extends `ReadValue<E>`. It is the root of the read side of the `*Comparable` combination lattice — the common supertype that every readable ordered value in this family satisfies. No new methods are declared; this is purely a bounded re-export of `ReadValue`.

## Notes

- **Does not carry the ordering operators.** Those live on `ReadDependencyComparable`, which additionally extends `Dependency`. A parameter typed as `ReadValueComparable` gives you only the plain `ReadValue` surface.
- `JustReadValueComparable` extends this to signal a functional (lambda-compatible) read.
