# `ReadWriteDependencyDouble`

Extends `ReadWriteValueDouble` and `ReadDependencyDouble`, overriding `negative`/`inverse` to write-back (`RW`) variants and adding RW arithmetic operator overloads.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`ReadWriteDependencyDouble extends ReadWriteValueDouble, ReadDependencyDouble, ReadWriteDependencyComparable<Double>`.

## Key behavior: RW operator override

`ReadDependencyDouble` declares `negative()` and `inverse()` as read-only (no write-back). `ReadWriteDependencyDouble` **overrides** them so that writing to the returned `SealDouble` propagates an appropriate value back to `this`:

- `negative()` / `negativeRW()` → `PileDouble.negativeRW(this)`
- `inverse()` / `inverseRW()` → `PileDouble.inverseRW(this)`

This is the main reason this interface exists as a separate level: to provide the write-back-capable arithmetic surface for values that support both reading and writing.

## Additional RW arithmetic overloads

All return `SealDouble`. Each has a plain name alias and an explicit `*RW` alias:

### Scalar (`double`) second operand
- `plus(double)` / `plusRW(double)` → `PileDouble.addRW(this, op2)`
- `minus(double)` / `minusRW(double)` → `PileDouble.subtractRW(this, op2)`
- `times(double)` / `timesRW(double)` → `PileDouble.multiplyRW(this, op2)`
- `over(double)` / `overRW(double)` → `PileDouble.divideRW(this, op2)`

### Reactive (`ReadListenDependency<? extends Number>`) second operand
- `plus(op2)` / `plusRW(op2)` → `PileDouble.addRW(this, op2)`
- `minus(op2)` / `minusRW(op2)` → `PileDouble.subtractRW(this, op2)`
- `times(op2)` / `timesRW(op2)` → `PileDouble.multiplyRW(this, op2)`
- `over(op2)` / `overRW(op2)` → `PileDouble.divideRW(this, op2)`

Note the second operand type differs from the RO variants in `ReadDependencyDouble`: RO takes `ReadDependency<? extends Number>`, while RW takes `ReadListenDependency<? extends Number>` (a narrower type — must be listenable to support write-back routing).

### Chaining
- `setNull()` — narrows return to `ReadWriteDependencyDouble`.

## Caveats
- **RO scalar overloads (`plusRO`/`minusRO`/`timesRO`/`overRO`) from `ReadDependencyDouble` are shadowed** for the scalar `double` case by the RW overloads here. The explicit `plusRO`/`overRO` etc. aliases on `ReadDependencyDouble` remain accessible but the plain-name overloads resolve to RW when called via this interface.
- Write-back behavior (how `PileDouble.negativeRW` etc. route writes) is defined in `PileDouble`; see `PileDouble.md`.

## See also
- [ReadDependencyDouble](ReadDependencyDouble.md) — the RO base
- [PileDouble](../PileDouble.md) — where RW arithmetic logic lives
- [combinations index](_index.md)
