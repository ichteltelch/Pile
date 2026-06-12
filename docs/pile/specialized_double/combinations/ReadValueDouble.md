# `ReadValueDouble`

Narrows [`ReadValue`](../../aspect/ReadValue.md) to `Double` and adds `getF()`, a null-safe boxed-`Float` view of the held value.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`ReadValueDouble extends ReadValueComparable<Double>`. It sits at the base of all double combination interfaces and is the first place double-specific read surface appears.

## What it adds

- `getF()` — returns the held `Double` as a `Float`, preserving `null`: if `get()` is `null`, returns `null`; otherwise calls `doubleValue()` then casts. Intended for APIs that require `Float` (e.g. certain graphics/UI bindings). There is no range- or precision-loss check.

## Caveats
- No primitive `double getAsDouble()` is declared here (unlike the bool analogue's `getAsBoolean`). Callers wanting an unboxed value must call `get()` and unbox manually, or use a concrete type's primitive accessor if present.
- `null` is passed through as `null` — callers must handle it.

## See also
- [JustReadValueDouble](JustReadValueDouble.md) — functional-interface stub
- [ReadDependencyDouble](ReadDependencyDouble.md) — adds arithmetic operators
- [combinations index](_index.md)
