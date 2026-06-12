# `ReadWriteListenDependencyInt`

The full non-recompute integer interface: narrows [`ReadWriteListenDependency`](../../aspect/combinations/ReadWriteListenDependency.md) to `Integer` and adds a writable `fallback(Integer)`.

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `ReadWriteListenValueInt`, `ReadListenDependencyInt`, `ReadWriteDependencyInt`, and `ReadWriteListenDependencyComparable<Integer>`. The only genuinely new member is `fallback(Integer v)` — delegates to `Piles.fallback(this, v)`, returning a `SealInt` that holds `v` while this is invalid and redirects writes back to `this`. A same-signature `fallback` is also declared on `ReadListenDependencyInt`; the version here is the writable one (the source is identical, but the surrounding interface has `ReadWriteDependency` methods). `setNull()` is narrowed to return `ReadWriteListenDependencyInt`. `PileInt` (the capstone) extends this interface and adds recompute, transactions, transform, and seal.

See [combinations index](_index.md) · [overview](../../../overview.md).
