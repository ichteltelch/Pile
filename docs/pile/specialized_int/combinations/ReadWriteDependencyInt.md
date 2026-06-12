# `ReadWriteDependencyInt`

Narrows [`ReadWriteDependency`](../../aspect/combinations/ReadWriteDependency.md) to `Integer`, overriding `negative()` and the constant-int arithmetic operators with writable variants that propagate writes back through the reactive chain.

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `ReadWriteValueInt`, `ReadDependencyInt`, and `ReadWriteDependencyComparable<Integer>`.

## New and overriding members

**Writable negation:** `negative()` / `negativeRW()` — both delegate to `PileInt.negativeRW(this)`. This **overrides** the read-only `negative()` from `ReadDependencyInt` (which delegates to `PileInt.negativeRO`): holding a `ReadWriteDependencyInt` gives a writable negation result; writes to the returned `SealInt` propagate back.

**Writable constant arithmetic:** `plus(int)` / `minus(int)` — delegate to `PileInt.addRW(this, op2)` and `PileInt.subtractRW(this, op2)`, overriding the RO versions from `ReadDependencyInt`. The explicit aliases `plusRW(int)` / `minusRW(int)` are also declared. Note that reactive-operand overloads (`plus(ReadDependency<Integer>)`) are **not** overridden; they remain read-only `SealInt` from `ReadDependencyInt`.

**`setNull()`** — narrowed return type to `ReadWriteDependencyInt`.

## Caveats
- Only `plus(int)` / `minus(int)` have writable overrides; `times(int)`, `integerDivide(int)`, etc. remain read-only from `ReadDependencyInt`. There are no `timesRW` or `divideRW` variants on this interface.

See [combinations index](_index.md) · [overview](../../../overview.md).
