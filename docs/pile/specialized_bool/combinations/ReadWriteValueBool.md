# `ReadWriteValueBool` — adds `flip()` to the read/write bool value surface

Source folder: `src`. Package: `pile.specialized_bool.combinations`.

Narrows [`ReadWriteValue`](../../aspect/combinations/ReadWriteValue.md) to `Boolean` by unioning [`ReadValueBool`](ReadValueBool.md) and [`WriteValueBool`](WriteValueBool.md), and adds one new member: `flip()` — toggles the current value (`set(!v)`). Silently returns (no-op) when the value is `null`; this is idiomatic null handling, not a bug.

Up: [combinations index](_index.md) · [bool package index](../_index.md) · [overview](../../../overview.md).
