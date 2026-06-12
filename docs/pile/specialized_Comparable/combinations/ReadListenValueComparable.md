# `ReadListenValueComparable`

Pure-assembly narrowing of [`ReadListenValue`](../../aspect/combinations/ReadListenValue.md) to `E extends Comparable<? super E>`; no new members.

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`ReadListenValueComparable<E>` extends `ReadValueComparable<E>` and `ReadListenValue<E>`. It adds the ability to observe changes (listen/bracket) to the read side of the comparable hierarchy, without making the value a reactive graph `Dependency`. No new methods are declared; the full `ReadListenValue` surface (buffer family, `asDependency`) is inherited from the generic interface.

## Notes

- **No ordering operators.** This type does not extend `ReadDependencyComparable`, so `lessThan`, `compareTo`, etc. are not available. The ordering surface requires `Dependency` status.
- `ReadListenDependencyComparable` extends both this and `ReadDependencyComparable`, giving both observation and the ordering operators.
