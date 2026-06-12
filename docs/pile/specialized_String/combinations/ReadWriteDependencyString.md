# `ReadWriteDependencyString` — pure assembly; narrows `setNull()` return type

Source folder: `src`. Package: `pile.specialized_String.combinations`.

Extends [`ReadWriteValueString`](ReadWriteValueString.md), [`ReadDependencyString`](ReadDependencyString.md), and [`ReadWriteDependencyComparable<String>`](../../specialized_Comparable/combinations/ReadWriteDependencyComparable.md) *(pending)*. The only body is a covariant override of `setNull()` returning `ReadWriteDependencyString`. No new logic; no writable-inversion operators (those belong to the bool family).

Up: [combinations index](_index.md) · [String package index](../_index.md) · [overview](../../../overview.md).
