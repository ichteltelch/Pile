# `WriteValueString` — adds `setEmpty()` and `setNull()` to the write surface

Source folder: `src`. Package: `pile.specialized_String.combinations`.

Extends [`WriteValueComparable<String>`](../../specialized_Comparable/combinations/WriteValueComparable.md) *(pending)*. Adds two default methods that are the only String-specific write conveniences in this family:

- `setEmpty()` — calls `set("")`. The only shortcut for the empty-string state (distinct from `null`).
- `setNull()` — calls `set(null)` and returns `this` for chaining. Re-declared here to give a covariant `WriteValueString` return type.

**Caveat:** `setEmpty()` and `setNull()` set distinct states — `""` vs `null`. They are not equivalent; do not confuse them when working with `nullableWrapper` (which encodes `null` as `""`).

Up: [combinations index](_index.md) · [String package index](../_index.md) · [overview](../../../overview.md).
