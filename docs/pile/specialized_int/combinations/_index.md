# `pile.specialized_int.combinations` — integer-specialized combination interfaces (Tier 1 map)

Twelve `*Int` interfaces that mirror the twelve generic [`pile.aspect.combinations`](../../aspect/combinations/_index.md) interfaces exactly, narrowed to `Integer`, and adding an integer-specific surface (primitive write conveniences, arithmetic/comparison operators, sign-negation, type coercion, and three-way branch selection).

Source folder: `src` (all interfaces below).

Up: [int package index](../_index.md) · [overview](../../../overview.md). Capstone concrete value: [`PileInt`](../PileInt.md). Generic counterparts: [aspect combinations index](../../aspect/combinations/_index.md). Bool sibling: [specialized_bool combinations index](../../specialized_bool/combinations/_index.md).

## What these interfaces are

Each `*Int` interface is a **thin assembly interface**: it unions its generic combination interface(s) (re-typed to `Integer`) and, where useful, contributes `int`-specific **default methods**. The substantive integer machinery — arithmetic operators, `min`/`max`, `negative`/`comparison`, `choose*` — lives in [`PileInt`](../PileInt.md) (static factories); the interfaces below are the **per-instance entry points** that delegate to it. Return types are narrowed to integer-specialized concrete types (`SealInt`, `IndependentInt`) or to other primitive-specialized types (`SealDouble`, `SealBool`, `SealString`) so callers stay in the primitive-specialized world without casts.

The 4-dimension lattice (Read / Write / Listen / Dependency) and the capstone role of `Pile` are explained in the [generic combinations index](../../aspect/combinations/_index.md); the same shape holds here, with `PileInt` as the integer capstone.

**Note on the Comparable intermediate layer.** Unlike the bool family, each `*Int` interface inherits through an intermediate `*Comparable<Integer>` layer (`pile.specialized_Comparable.combinations`). That layer contributes comparison operators (`compareTo`, `greaterThan`, `lessThan`, `greaterOrEqual`, `lessOrEqual`, `equalTo`) returning `SealBool`/`SealInt`. The per-interface docs below count inherited members from that layer as part of each interface's surface.

## Map: `*Int` → generic counterpart

| `*Int` interface | Mirrors (generic) | Narrows / adds |
|---|---|---|
| `JustReadValueInt` | [`JustReadValue`](../../aspect/JustReadValue.md) (+ `ReadValueInt`, `JustReadValueComparable<Integer>`) | Pure stub; no new members beyond what `ReadValueInt` + Comparable supply. |
| `ReadValueInt` | [`ReadValue`](../../aspect/ReadValue.md) (via `ReadValueComparable<Integer>`) | Pure narrowing through the Comparable layer; no new members here. |
| `ReadDependencyInt` | [`ReadDependency`](../../aspect/combinations/ReadDependency.md) (via `ReadDependencyComparable<Integer>`) | **Adds** arithmetic operators (`plus`/`minus`/`times`/`over`/`integerDivide`/`remainder`/`modulo`), int/double primitives variants, `min`/`max`, sign `negative`/`negativeRO`, `toDouble`, `readOnly`, `overridable`, and the full `choose*`/`chooseWritable*`/`chooseConst*` family (3-way branch on negative/zero/positive/null). |
| `ReadListenValueInt` | [`ReadListenValue`](../../aspect/combinations/ReadListenValue.md) | Narrows buffer family (`buffer`/`validBuffer`/`rateLimited` + builders) to `SealInt`/`IndependentInt`. No new logic. |
| `ReadListenDependencyInt` | [`ReadListenDependency`](../../aspect/combinations/ReadListenDependency.md) | **Adds** `fallback(Integer)` (constant fallback while invalid; writes redirect back). |
| `WriteValueInt` | [`WriteValue`](../../aspect/WriteValue.md) (via `WriteValueComparable<Integer>`) | **Adds** `setZero()`, `setOne()`, `setNull()` (returns `this`). |
| `WriteElsewhereInt` | [`WriteElsewherePile`](../../aspect/combinations/WriteElsewherePile.md) | Entirely commented out (inert), mirroring the dead generic `WriteElsewhere*` idea. |
| `ReadWriteValueInt` | [`ReadWriteValue`](../../aspect/combinations/ReadWriteValue.md) | **Adds** `flip()` (sign-negate; no-op on `null`); `setNull()` returning `this`. |
| `ReadWriteDependencyInt` | [`ReadWriteDependency`](../../aspect/combinations/ReadWriteDependency.md) | **Adds** writable `negative()`/`negativeRW()` (delegates to `PileInt.negativeRW`; writes redirect back), and `plus(int)`/`minus(int)` overloads that return writable `SealInt` via `PileInt.addRW`/`subtractRW`. |
| `ReadWriteListenValueInt` | [`ReadWriteListenValue`](../../aspect/combinations/ReadWriteListenValue.md) | Narrows the **writable** buffer family to `SealInt`/`IndependentInt`; `setNull()`. No new logic. |
| `ReadWriteListenDependencyInt` | [`ReadWriteListenDependency`](../../aspect/combinations/ReadWriteListenDependency.md) | Full non-recompute integer contract; adds `fallback(Integer)` (writable redirect). |
| `LastValueRemembererInt` | [`LastValueRememberer`](../../aspect/LastValueRememberer.md) | Pure narrowing to `Integer`; no new members. |

`PileInt` (the integer capstone, in the parent package) extends `ReadWriteListenDependencyInt` and adds recompute/transaction/transform/seal — exactly as generic `Pile` sits atop `ReadWriteListenDependency`.

## The integer-specific surface (what's callable where)

### Primitive write conveniences — `WriteValueInt`
- `setZero()` — calls `set(0)`.
- `setOne()` — calls `set(1)`.
- `setNull()` — calls `set(null)`, returns `this` for chaining (overridden at each level to narrow the return type).

### Arithmetic operators — `ReadDependencyInt` (default methods, each delegates to `PileInt`)
These are the **per-instance entry points** to the operators that [`PileInt`](../PileInt.md) also exposes statically.

**Integer-integer, return `SealInt`:**
- `plus(ReadDependency<? extends Integer>)` / `minus(...)` / `times(...)` — reactive int arithmetic.
- `integerDivide(...)` / `remainder(...)` / `modulo(...)` — divide, Java-remainder, and floored modulo.
- `plus(int)` / `minus(int)` / `times(int)` / `integerDivide(int)` / `remainder(int)` / `modulo(int)` — constant-operand variants (RO; the RW versions appear on `ReadWriteDependencyInt`).
- `plusRO(int)` / `minusRO(int)` — explicitly read-only aliases (mirror the `*RO`/`*RW` naming).

**Widening to double, return `SealDouble`:**
- `over(ReadDependency<? extends Number>)` / `over(double)` / `over(int)` — true double division.
- `plus(ReadDependencyDouble)` / `minus(ReadDependencyDouble)` / `times(ReadDependencyDouble)` — int+double mixed-mode.
- `plus(double)` / `minus(double)` / `times(double)` / `plusRO(double)` / `minusRO(double)` — constant-double variants.

**Minmax (int and double overloads):**
- `min(ReadDependency<? extends Integer>)` / `max(...)` — reactive int min/max.
- `min(int)` / `max(int)` — constant-operand int min/max.
- `min(ReadDependencyDouble)` / `max(ReadDependencyDouble)` / `min(double)` / `max(double)` — widening double variants.

**Sign:**
- `negative()` / `negativeRO()` — read-only negation; returns `SealInt`. On `ReadWriteDependencyInt`, `negative()` is **overridden** to call `PileInt.negativeRW` so that writes to the result propagate back through.

**Type coercion:**
- `toDouble()` — maps to `SealDouble` by casting; `null` → `null`.
- `readOnly()` — wraps in a read-only `SealInt`.
- `overridable()` — wraps in a `PileIntImpl` recompute node that re-reads whenever this changes; returned type name includes `*` suffix.

### Three-way branch selection — `ReadDependencyInt`
Unlike the bool family's two-way `choose(ifTrue, ifFalse)`, the int family uses a **four-arm** `choose(ifNeg, ifZero, ifPos[, ifNull])` — the selected branch depends on the sign (negative / zero / positive / null) of this reactive integer. Each arm can be:
- a `ReadDependency` → `choose(...)` returning `SealPile<E>`;
- a `ReadWriteDependency` → `chooseWritable(...)` returning `SealPile<E>` with write-back;
- a plain constant value → `chooseConst(...)`.
Type-specialized variants narrow the return to `SealInt`, `SealBool`, `SealDouble`, or `SealString`: `chooseInt`, `chooseWritableInt`, `chooseConstInt`, `chooseBool`, etc. The underlying work is done by `PileInt._choose`, `PileInt._chooseWritable`, and `PileInt._chooseConst`; the `_choose*` methods on this interface are the template entry points.

### Writable arithmetic — `ReadWriteDependencyInt`
- `plus(int)` / `minus(int)` — **overrides** the `ReadDependencyInt` RO versions; delegates to `PileInt.addRW`/`subtractRW` so that writes to the result propagate back to `this`.
- `plusRW(int)` / `minusRW(int)` — explicit RW aliases.
- `negative()` — **overrides** the RO version from `ReadDependencyInt`; delegates to `PileInt.negativeRW`.

### Buffer family — `ReadListenValueInt` / `ReadWriteListenValueInt`
Narrows the generic buffer overloads to return `SealInt` (from `SealPileBuilder<SealInt, Integer>`) and `IndependentInt` (from `IndependentBuilder<IndependentInt, Integer>`). Same three flavours as bool: `buffer`, `validBuffer`/`validBuffer_memo`, `rateLimited`.

### Fallback — `ReadListenDependencyInt` / `ReadWriteListenDependencyInt`
`fallback(Integer v)` — delegates to `Piles.fallback(this, v)`; the returned `SealInt` holds `v` whenever `this` is invalid. On `ReadWriteListenDependencyInt` the fallback is writable (writes redirect to `this`). Both overrides have the same signature; the writable version is the one that sees `ReadWriteDependency` methods.

### Flip — `ReadWriteValueInt`
`flip()` — negates the value in place (`set(-v)`). **Silently returns** (no-op) when the value is `null` (idiomatic null handling, not a bug).

## Caveats & gotchas
- **`null` is a first-class third state.** All arithmetic returns `null` when any operand is `null`; `flip()` no-ops on `null`; `choose(ifNeg, ifZero, ifPos)` (3-arm) maps `null` to `null` via `constNull()`, while `choose(ifNeg, ifZero, ifPos, ifNull)` (4-arm) routes through `ifNull`.
- **RO vs RW operator dispatch.** `plus(int)` and `minus(int)` on `ReadDependencyInt` produce a read-only `SealInt` (via `addRO`/`subtractRO`); the same `plus(int)`/`minus(int)` on `ReadWriteDependencyInt` produce a **writable** `SealInt` (via `addRW`/`subtractRW`). Java resolves the correct version at the declared type; callers should be aware of which they hold.
- **`over` always widens to `SealDouble`.** There is no integer-division operator named `over`; `integerDivide` fills that role.
- **`remainder` vs `modulo`.** `remainder` is Java's `%` (can be negative); `modulo` is the floored variant (always non-negative for positive divisor). Semantics live in `PileInt`.
- **The Comparable layer adds comparison operators.** `greaterThan`, `lessThan`, `greaterOrEqual`, `lessOrEqual`, `equalTo`, `compareTo`, `compareToConst` — returning `SealBool`/`SealInt` — are inherited from `ReadDependencyComparable<Integer>`; they appear on all `ReadDependencyInt` subtypes but are not re-declared here.
- **`WriteElsewhereInt` is inert** — wholly commented out, present only to mirror the dead generic deferred-write concept.
- **`validBuffer_memo` vs `validBuffer`.** The memo variant (`validBuffer_memo`) is cached per-source; the plain `validBuffer` always creates a new instance. Choose deliberately.

## Common tasks
- Set a reactive integer to zero/one → `setZero()` / `setOne()`.
- Compute a derived reactive sum/difference → `a.plus(b)` / `a.minus(b)` (or static `PileInt.add(a, b)`).
- Divide reactively into a double → `a.over(b)` (integer `b`) or `a.over(bDouble)`.
- Integer division / modulo → `a.integerDivide(b)` / `a.modulo(b)`.
- Clamp to a range → `a.max(lo).min(hi)` (chain).
- Negate reactively (read-only) → `a.negative()` or `a.negativeRO()`.
- Negate with write-back → hold a `ReadWriteDependencyInt` and call `a.negative()` (routes to `negativeRW`).
- Branch on sign → `n.choose(ifNeg, ifZero, ifPos)` / `n.chooseConst(v1, v2, v3)`.
- Convert to double reactively → `a.toDouble()`.
- Buffer against rapid changes → `a.buffer()` / `a.validBuffer()`.

## Tech debt / warts
- `WriteElsewhereInt` is dead commented-out code (mirrors the generic `WriteElsewherePile` vestige).
- The `plusRO`/`minusRO` alias methods on `ReadDependencyInt` duplicate the plain `plus`/`minus` (same body). The `*RO` names exist to make intent explicit in contexts where both `ReadDependencyInt` and `ReadWriteDependencyInt` are in scope.
- `times(int)` on `ReadDependencyInt` inconsistently calls `PileInt.multiply(this, op2)` rather than `PileInt.multiplyRO(this, op2)` — both overloads appear to exist on `PileInt`, but the javadoc on the method says "Delegates to `PileInt#multiplyRO`" while the body calls `multiply`. This is a potential naming inconsistency (not a behavioral bug if the two are equivalent, but worth verifying).
