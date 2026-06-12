# `ReadWriteListenDependencyBool` — full non-recompute boolean contract; resolves the `not()` diamond

Source folder: `src`. Package: `pile.specialized_bool.combinations`.

Narrows [`ReadWriteListenDependency`](../../aspect/combinations/ReadWriteListenDependency.md) to `Boolean` by unioning [`ReadWriteListenValueBool`](ReadWriteListenValueBool.md), [`ReadListenDependencyBool`](ReadListenDependencyBool.md), and [`ReadWriteDependencyBool`](ReadWriteDependencyBool.md). This is the full read/write/listen/dependency boolean interface short of recompute; `PileBool` sits above it and adds recompute/transaction/transform.

## New members

- `setNull()` — `set(null)` returning `ReadWriteListenDependencyBool` for chaining (covariant narrowing over the `ReadWriteListenValueBool` version).
- `fallback(Boolean v)` — writable variant: a `SealBool` that takes constant `v` when `this` is invalid; writes to the result redirect back to `this`. Delegates to `Piles.fallback`.
- `not()` — explicitly resolves the diamond by delegating to `ReadWriteDependencyBool.super.not()`, ensuring the writable `notRW` path wins over `ReadDependencyBool`'s `notRO`.

Up: [combinations index](_index.md) · [bool package index](../_index.md) · [overview](../../../overview.md).
