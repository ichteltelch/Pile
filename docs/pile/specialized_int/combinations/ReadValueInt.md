# `ReadValueInt`

Pure narrowing interface combining [`ReadValue`](../../aspect/ReadValue.md) with `ReadValueComparable<Integer>`; adds no members of its own.

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `ReadValueComparable<Integer>`, which itself extends the generic `ReadValue<Integer>`. All type narrowing (e.g., `get()` returning `Integer`) comes from the Comparable layer. The integer-specific primitive read surface (such as `getAsInt()`) does **not** live here — `MutInt` (a bare mutable box in the parent package) is the only place that provides a raw `int` accessor. See [combinations index](_index.md) for the full surface.
