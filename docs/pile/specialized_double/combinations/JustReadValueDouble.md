# `JustReadValueDouble`

Pure narrowing of [`JustReadValue`](../../aspect/JustReadValue.md) to `Double` via [`ReadValueDouble`](ReadValueDouble.md); no new members.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`JustReadValueDouble extends ReadValueDouble, JustReadValueComparable<Double>`. It is the `@FunctionalInterface`-compatible stub for a plain `Double` supplier in the reactive system — a read-only snapshot value with no listener or dependency machinery.

## What it adds
Nothing beyond what `ReadValueDouble` (and transitively `JustReadValue`) already provide. The sole purpose is type narrowing so that code requiring a `JustReadValueDouble` can receive one without a cast.

## See also
- [ReadValueDouble](ReadValueDouble.md) — the read surface this extends
- [package index](../../../overview.md) · [combinations index](_index.md)
