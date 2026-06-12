# `ReadListenDependencyInt`

Narrows [`ReadListenDependency`](../../aspect/combinations/ReadListenDependency.md) to `Integer` and adds `fallback(Integer)` — a constant-fallback value active while this reactive integer is invalid.

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `ReadListenDependencyComparable<Integer>`, `ReadListenValueInt`, and `ReadDependencyInt`. The only new member is `fallback(Integer v)`, which delegates to `Piles.fallback(this, v)`: the returned `SealInt` holds the constant `v` whenever `this` is invalid, and writes to it are redirected back to `this`. The writable counterpart of this method appears on `ReadWriteListenDependencyInt`.

See [combinations index](_index.md) · [overview](../../../overview.md).
