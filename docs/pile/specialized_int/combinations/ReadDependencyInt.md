# `ReadDependencyInt`

The richest read-side interface in the integer combination hierarchy: narrows [`ReadDependency`](../../aspect/combinations/ReadDependency.md) to `Integer` and adds the full integer operator surface as instance entry points that delegate to [`PileInt`](../PileInt.md).

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `ReadValueInt`, `Dependency`, and `ReadDependencyComparable<Integer>`. The Comparable layer contributes comparison operators (`greaterThan`, `lessThan`, `greaterOrEqual`, `lessOrEqual`, `equalTo`, `compareTo`, `compareToConst`) returning `SealBool`/`SealInt`.

## New members

**Arithmetic (all return `SealInt` or `SealDouble`):** `plus`, `minus`, `times`, `over` (→ `SealDouble`), `integerDivide`, `remainder`, `modulo` — each with a reactive `ReadDependency<Integer>` overload and an `int`/`double` constant overload. The constant-int variants (`plus(int)`, `minus(int)`) are **read-only** here; `ReadWriteDependencyInt` overrides them with writable versions via `PileInt.addRW`/`subtractRW`. Explicit RO aliases `plusRO(int)` / `minusRO(int)` are also provided.

**Sign:** `negative()` / `negativeRO()` — both delegate to `PileInt.negativeRO`; the javadoc on `negative()` reminds subclasses that implement `WriteValue` to override with `PileInt.negativeRW` instead (which `ReadWriteDependencyInt` does).

**Min/max:** `min`/`max` with `ReadDependency<Integer>`, `int`, `ReadDependencyDouble`, and `double` overloads.

**Type coercion:** `toDouble()` — maps `null`→`null`, non-null→`Double`; `readOnly()` — wraps in a read-only `SealInt`; `overridable()` — wraps in a `PileIntImpl` recompute node that invalidates whenever this changes.

**Three-way branch (`choose*`):** `choose(ifNeg, ifZero, ifPos[, ifNull])`, `chooseWritable(...)`, `chooseConst(...)` plus per-type specializations returning `SealInt`, `SealBool`, `SealDouble`, `SealString`. All delegate to `PileInt._choose*` which uses a `SealPile` template. See [combinations index](_index.md) for full detail.

## Caveats
- `times(int op2)` calls `PileInt.multiply(this, op2)` while its javadoc says "Delegates to `PileInt#multiplyRO`" — verify whether `PileInt.multiply(ReadDependency, int)` and `PileInt.multiplyRO` are identical overloads.
- `over(int)` widens to `SealDouble` (true division), not `SealInt`.

See [combinations index](_index.md) · [overview](../../../overview.md).
