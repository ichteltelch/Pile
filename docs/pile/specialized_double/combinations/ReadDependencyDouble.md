# `ReadDependencyDouble`

Narrows [`ReadDependency`](../../aspect/combinations/ReadDependency.md) to `Double` and adds the full read-side arithmetic/math operator surface, all delegating to [`PileDouble`](../PileDouble.md).

Source folder: `src` · Package: `pile.specialized_double.combinations`

`ReadDependencyDouble extends ReadValueDouble, Dependency, ReadDependencyComparable<Double>`.

## Operators and math methods (all `default`, all return `SealDouble` unless noted)

### Arithmetic — reactive second operand (`ReadDependency<? extends Number>`)
- `plus(op2)` → `PileDouble.add(this, op2)`
- `minus(op2)` → `PileDouble.subtract(this, op2)`
- `times(op2)` → `PileDouble.multiply(this, op2)`
- `over(op2)` → `PileDouble.divide(this, op2)`

### Arithmetic — scalar second operand (`double`)
- `plus(double)` / `plusRO(double)` → `PileDouble.addRO(this, op2)`
- `minus(double)` / `minusRO(double)` → `PileDouble.subtractRO(this, op2)`
- `times(double)` / `timesRO(double)` → `PileDouble.multiplyRO(this, op2)`
- `over(double)` / `overRO(double)` → `PileDouble.divideRO(this, op2)`

The `*RO` aliases are explicit to contrast with the `*RW` variants in `ReadWriteDependencyDouble`.

### Unary
- `negative()` / `negativeRO()` → `PileDouble.negativeRO(this)` — sign negation.
- `inverse()` / `inverseRO()` → `PileDouble.inverseRO(this)` — multiplicative inverse (1/x).

### Clamping
- `min(ReadDependency<? extends Number>)` → `PileDouble.min(this, op2)`
- `max(ReadDependency<? extends Number>)` → `PileDouble.max(this, op2)`
- `min(double)` → `PileDouble.min(this, op2)`
- `max(double)` → `PileDouble.max(this, op2)`

### Coercion / wiring
- `round()` → returns a `SealInt` via `mapToInt`; uses `Math.round` on the `double`; maps `null` to `null`.
- `readOnly()` → `Piles.makeReadOnlyWrapper(this, new SealDouble())` — a read-only `SealDouble` mirror.
- `overridable()` → `Piles.computeDouble(this).whenChanged(this)` — a `PileDoubleImpl` that recomputes from this but accepts override writes.

## Caveats
- **RO vs RW split**: `negative()` / `inverse()` here are read-only (no write-back). In `ReadWriteDependencyDouble` they are overridden to write-back variants. Calling `negative()` on a value that implements both gives the RW version automatically via Java interface resolution.
- All operators return `SealDouble` — a sealed/read-only reactive type. Write-back-capable variants require `ReadWriteDependencyDouble`.
- `null` propagation is handled by `PileDouble`; see `PileDouble.md` for details.

## See also
- [PileDouble](../PileDouble.md) — where the actual arithmetic logic lives
- [ReadWriteDependencyDouble](ReadWriteDependencyDouble.md) — RW override of `negative`/`inverse` and additional RW overloads
- [combinations index](_index.md)
