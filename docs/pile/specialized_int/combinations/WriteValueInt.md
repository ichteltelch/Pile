# `WriteValueInt`

Narrows [`WriteValue`](../../aspect/WriteValue.md) to `Integer` and adds `setZero()`, `setOne()`, and a chaining `setNull()`.

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `WriteValueComparable<Integer>`. New members: `setZero()` calls `set(0)`; `setOne()` calls `set(1)`; `setNull()` calls `set(null)` and returns `this` (return type narrowed to `WriteValueInt`). Subinterfaces override `setNull()` to narrow the return type further (e.g., `ReadWriteValueInt`, `ReadWriteDependencyInt`, etc.).

See [combinations index](_index.md) · [overview](../../../overview.md).
