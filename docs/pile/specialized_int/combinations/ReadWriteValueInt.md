# `ReadWriteValueInt`

Narrows [`ReadWriteValue`](../../aspect/combinations/ReadWriteValue.md) to `Integer`, unifying `ReadValueInt` and `WriteValueInt`, and adds `flip()` (in-place sign negation).

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `ReadValueInt`, `WriteValueInt`, and `ReadWriteValueComparable<Integer>`. New members: `flip()` reads the current value and calls `set(-v)`; **silently returns without action** when the value is `null` (idiomatic null handling). `setNull()` is also overridden to narrow the return type to `ReadWriteValueInt` for chaining.

See [combinations index](_index.md) · [overview](../../../overview.md).
