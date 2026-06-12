# `ReadWriteDependencyBool` — writable boolean operators: `not`/`notRW`, `invertIf`, `setNull`

Source folder: `src`. Package: `pile.specialized_bool.combinations`.

Narrows [`ReadWriteDependency`](../../aspect/combinations/ReadWriteDependency.md) to `Boolean` by unioning [`ReadWriteValueBool`](ReadWriteValueBool.md) and [`ReadDependencyBool`](ReadDependencyBool.md), and adds the write-side boolean operator surface.

## New members

- `not()` / `notRW()` — override `ReadDependencyBool.not()` to call `PileBool.notRW(this)` instead of `notRO`; returns a `ReadWriteListenDependencyBool` whose writes are inverted and passed back to `this`.
- `setNull()` — `set(null)` returning `this` for fluent chaining.
- `invertIf(control)` — controlled NOT via `PileBool.cNot(this, control)`: the result is `this XOR control`; writes to the result attempt an appropriate write-back into `this`.

## Caveats

The `not()` override here is critical: interfaces with both read and write capability must use `notRW` (bidirectional write-back), not `notRO`. [`ReadWriteListenDependencyBool`](ReadWriteListenDependencyBool.md) further explicitly resolves the diamond to this version.

Up: [combinations index](_index.md) · [bool package index](../_index.md) · [overview](../../../overview.md).
