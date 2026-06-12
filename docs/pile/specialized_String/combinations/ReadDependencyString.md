# `ReadDependencyString` — `readOnly()` and `overridable()` narrowed to `String` types

Source folder: `src`. Package: `pile.specialized_String.combinations`.

Extends [`ReadValueString`](ReadValueString.md), [`Dependency`](../../aspect/Dependency.md), and [`ReadDependencyComparable<String>`](../../specialized_Comparable/combinations/ReadDependencyComparable.md) *(pending)*. Adds two default methods that narrow the generic wrappers to String-specialized return types:

- `readOnly()` — returns a `SealString` read-only view via `Piles.makeReadOnlyWrapper`.
- `overridable()` — returns a `PileStringImpl` that recomputes from `this` but can be locally overridden; uses `Piles.computeString(this).name(dependencyName()+"*").whenChanged(this)`. The trailing `*` in the name is a naming convention marking it as a mutable override of its source.

No reactive operators (no `and`/`or`/`not`, no `choose*`) — those belong to the bool family. The comparison surface (ordering, min/max) is inherited from `ReadDependencyComparable`.

Up: [combinations index](_index.md) · [String package index](../_index.md) · [overview](../../../overview.md).
