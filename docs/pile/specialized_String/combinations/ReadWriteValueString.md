# `ReadWriteValueString` — pure assembly; narrows `setNull()` return type

Source folder: `src`. Package: `pile.specialized_String.combinations`.

Extends [`ReadValueString`](ReadValueString.md), [`WriteValueString`](WriteValueString.md), and [`ReadWriteValueComparable<String>`](../../specialized_Comparable/combinations/ReadWriteValueComparable.md) *(pending)*. The only body is a covariant override of `setNull()` returning `ReadWriteValueString` (calls `set(null)` and returns `this`). No new logic; no `flip()` (that was bool-specific).

Up: [combinations index](_index.md) · [String package index](../_index.md) · [overview](../../../overview.md).
