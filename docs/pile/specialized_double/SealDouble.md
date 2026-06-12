# `SealDouble`

Sealable reactive `double`; the concrete type returned by every redirecting double arithmetic/comparison operator.

Source folder: `src`. Package: `pile.specialized_double`.

Up: [_index.md](_index.md) · [overview](../../overview.md). Generic counterpart: [../impl/SealPile.md](../impl/SealPile.md). Operator surface that produces `SealDouble`: [_index.md](_index.md) § The double-specific operator surface.

## What it is

`SealDouble` extends `SealComparable<Double>` and implements both `ReadWriteListenDependencyDouble` and `PileDouble`. It is the concrete output type of every redirecting double operator (`negative`, `plus`, `times`, `over`, `min`, `max`, …) — the caller receives a `SealDouble` whose recompute function applies the operator and whose optional write-back bijection propagates writes upstream.

Because `SealDouble` implements `PileDouble`, it exposes the full arithmetic operator surface as instance methods, so chains like `a.plus(b).times(c)` stay typed as `SealDouble` throughout.

## Delta over the generic

The body adds only two typed overrides:
- `setName(String)` — writes `avName` directly and returns `this` (typed `SealDouble`).
- `setNull()` — calls `set(null)` and returns `this`.

All sealing / redirecting / write-back logic lives in the generic `SealPile` / `SealComparable` chain. `SealDouble` contributes nothing new to reactive semantics.

## Key behavioral note

`SealDouble` as a return type is a strong signal: the value is **derived and potentially write-back-capable** (if a bijection was installed by the factory). Whether writes propagate depends entirely on how the `SealDouble` was constructed by the `PileDouble` factory. Read-only arithmetic factories (`negativeRO`, `plusRO`, …) produce a `SealDouble` with no bijection installed; `RW` factories install a bijection so writes invert the operation and set the source operand.

## Caveats & gotchas

- **`RW` bijection round-trip:** write-back only round-trips cleanly for invertible operations. `divideRW` is documented as write-back capable but as of the current source delegates to `multiplyRO` — the bijection is not installed. See [_index.md](_index.md) § Tech debt / warts.
- **`inverseRW` discrepancy:** recompute computes negation (`-v`) while write-back computes reciprocal (`1/v`). See [_index.md](_index.md) § Tech debt / warts.
