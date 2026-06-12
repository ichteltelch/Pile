# `WriteValueDouble`

Narrows [`WriteValue`](../../aspect/WriteValue.md) to `Double` and adds write conveniences for common `double` constants.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`WriteValueDouble extends WriteValueComparable<Double>`.

## What it adds

All are `default` void (except `setNull`):
- `setZero()` — `set(0.0)`
- `setOne()` — `set(1.0)`
- `setPositiveInfinte()` — `set(Double.POSITIVE_INFINITY)` (**typo**: "Infinte", missing second 'i')
- `setNegativeInfinite()` — `set(Double.NEGATIVE_INFINITY)`
- `setNull()` — `set(null)`, returns `this` (chains as `WriteValueDouble`)

## Caveats
- **`setPositiveInfinte` is a typo** in the method name. Do not fix without searching all call sites; the signature is part of the public API.

## See also
- [ReadWriteValueDouble](ReadWriteValueDouble.md) — adds `flip()` sign-negation
- [combinations index](_index.md)
