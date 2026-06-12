# `ReadDependencyBool` — read-side boolean operators and reactive choice, narrowing `ReadDependency<Boolean>`

Source folder: `src`. Package: `pile.specialized_bool.combinations`.

Narrows [`ReadDependency`](../../aspect/combinations/ReadDependency.md) to `Boolean` and adds the entire read-side boolean operator and multiplexer surface. All operator default methods delegate to [`PileBool`](../PileBool.md) statics; this interface is the per-instance entry point, not the logic host.

## New members

### Logic operators
- `and(op2)` / `andScd(op2)` — delegating to `PileBool.and` / `PileBool.andScd`; return `SealBool`.
- `or(op2)` / `orScd(op2)` — delegating to `PileBool.or` / `PileBool.orScd`; return `SealBool`.
- `not()` / `notRO()` — read-only inversion via `PileBool.notRO`; return `ReadListenDependencyBool`. Subclasses with write capability should override `not()` to call `notRW` instead (see [`ReadWriteDependencyBool`](ReadWriteDependencyBool.md)).

### Reactive multiplexers (`choose` family)
- `choose(ifTrue, ifFalse[, ifNull])` — reactive branch on three-valued chooser; returns `SealPile<E>`.
- `chooseWritable(ifTrue, ifFalse[, ifNull])` — bidirectional: writes forwarded to active branch.
- `chooseConst(ifTrue, ifFalse[, ifNull])` — constant branches.
- `chooseConstV(ifTrue, ifFalse)` / `chooseVConst(ifTrue, ifFalse)` — mix one reactive arm with one constant.
- Typed specializations: `chooseInt`/`chooseWritableInt`/`chooseConstInt`, `chooseBool`/`chooseWritableBool`/`chooseConstBool`, `chooseDouble`/`chooseWritableDouble`/`chooseConstDouble`, `chooseString`/`chooseWritableString`/`chooseConstString` — all route through `_choose`/`_chooseWritable`/`_chooseConst` with the matching typed `SealPile` template.
- `_choose` / `_chooseWritable` / `_chooseConst` — protected-style template primitives accepting a caller-supplied `SealPile` template; delegate directly to `PileBool`.

### Coercion / wiring
- `mapToInt()` — `true`→1, else→0 via the static `__BOOL_TO_INT` function field; returns `SealInt`.
- `readOnly()` — wraps `this` in a `SealBool` read-only wrapper via `Piles.makeReadOnlyWrapper`.
- `overridable()` — builds a `PileBoolImpl` that recomputes from `this` but can be overridden locally.
- `validIfTrue()` — a `Dependency` valid only while this holds `true`; delegates to `PileBool.validIfTrue`.

## Caveats

When `ifNull` is omitted from `choose`/`chooseWritable`, it defaults to `Piles.constNull()`, so the result is reactive-null (not invalid). The 2-arg `chooseConst` defaults the `ifNull` slot to the Java literal `null`, which also produces a reactive null. See [`PileBool`](../PileBool.md) for the full operator algebra and truth tables.

Up: [combinations index](_index.md) · [bool package index](../_index.md) · [overview](../../../overview.md).
