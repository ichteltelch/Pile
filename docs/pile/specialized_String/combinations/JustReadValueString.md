# `JustReadValueString` — pure assembly; narrows `JustReadValueComparable` to `String`

Source folder: `src`. Package: `pile.specialized_String.combinations`.

Pure assembly stub. Extends [`ReadValueString`](ReadValueString.md) and [`JustReadValueComparable<String>`](../../specialized_Comparable/combinations/JustReadValueComparable.md) *(pending)*; no new members. The functional contract inherited from [`JustReadValue`](../../aspect/JustReadValue.md) is a `String`-returning `get()`. Note that unlike `JustReadValueBool`, this interface does **not** carry a `@FunctionalInterface` annotation in its own declaration (the annotation may be inherited or absent — check `JustReadValueComparable`).

Up: [combinations index](_index.md) · [String package index](../_index.md) · [overview](../../../overview.md).
