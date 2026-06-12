# `ReadWriteListenDependencyString` — full writable String contract; adds `nullableWrapper()` and `fallback(String)`

Source folder: `src`. Package: `pile.specialized_String.combinations`.

Extends [`ReadWriteListenValueString`](ReadWriteListenValueString.md), [`ReadListenDependencyString`](ReadListenDependencyString.md), [`ReadWriteDependencyString`](ReadWriteDependencyString.md), and [`ReadWriteListenDependencyComparable<String>`](../../specialized_Comparable/combinations/ReadWriteListenDependencyComparable.md) *(pending)*. This is the full non-recompute String contract — the interface that `PileString` (the capstone) extends.

Adds two default methods beyond what the parent interfaces contribute:

- `nullableWrapper()` — delegates to `PileString.nullableWrapper(this)`; returns a `SealString` that stores `null` as `""` and escapes a real `""` or leading-space string with a prepended space. See [`../_index.md`](../_index.md) for the null-vs-`""` encoding semantics.
- `fallback(String v)` — delegates to `Piles.fallback(this, v)`; returns a `SealString` that holds `v` while `this` is invalid and redirects writes back to `this`. (Also declared on `ReadListenDependencyString`; re-declared here for the covariant context.)
- `setNull()` — covariant override returning `ReadWriteListenDependencyString`.

**Common task:** to bind a reactive String to a GUI text field that cannot hold `null`, call `nullableWrapper()` and bind the returned `SealString` — reads and writes through the wrapper transparently encode/decode the null state.

Up: [combinations index](_index.md) · [String package index](../_index.md) · [overview](../../../overview.md).
