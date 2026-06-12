# `ReadListenDependencyString` — adds `fallback(String)` to the read-listen-dependency surface

Source folder: `src`. Package: `pile.specialized_String.combinations`.

Extends [`ReadListenDependencyComparable<String>`](../../specialized_Comparable/combinations/ReadListenDependencyComparable.md) *(pending)*, [`ReadListenValueString`](ReadListenValueString.md), and [`ReadDependencyString`](ReadDependencyString.md). Adds one default method:

- `fallback(String v)` — delegates to `Piles.fallback(this, v)`; returns a `SealString` that takes on the constant value `v` whenever `this` is invalid. Writes to the returned value are redirected back to `this`.

No other new members beyond what the parent interfaces already provide.

Up: [combinations index](_index.md) · [String package index](../_index.md) · [overview](../../../overview.md).
