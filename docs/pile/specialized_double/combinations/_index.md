# `pile.specialized_double.combinations` — double-specialized combination interfaces (Tier 1 map)

Twelve `*Double` interfaces that mirror the twelve generic [`pile.aspect.combinations`](../../aspect/combinations/_index.md) interfaces exactly, narrowed to `Double`, and adding a double-specific surface (primitive `float`-boxed read accessor, arithmetic operators, `round`, `negative`/`inverse`, `min`/`max`, and write conveniences).

Source folder: `src` (all interfaces below).

Up: [double package index](../_index.md) · [overview](../../../overview.md). Capstone concrete value: [`PileDouble`](../PileDouble.md). Generic counterparts: [aspect combinations index](../../aspect/combinations/_index.md). Bool analogue: [bool combinations index](../../specialized_bool/combinations/_index.md).

## What these interfaces are

Each `*Double` interface is a **thin assembly interface**: it unions its generic combination interface(s) (re-typed to `Double`) and, where useful, contributes `double`-specific **default methods**. The substantive double arithmetic — `add`/`subtract`/`multiply`/`divide`, `negative`/`inverse`, `min`/`max`, `round`, and the RO vs RW variants — lives in [`PileDouble`](../PileDouble.md); the interfaces below are the **per-instance entry points** that delegate to it. Return types are narrowed to the double-specialized concrete types ([`SealDouble`](../SealDouble.md), [`IndependentDouble`](../IndependentDouble.md), [`PileDoubleImpl`](../PileDoubleImpl.md)) so callers stay in the primitive-specialized world without casts.

The 4-dimension lattice (Read / Write / Listen / Dependency) and the capstone role of `Pile` are explained in the [generic combinations index](../../aspect/combinations/_index.md); the same shape holds here, with `PileDouble` as the double capstone.

## Map: `*Double` → generic counterpart

| `*Double` interface | Mirrors (generic) | Narrows / adds |
|---|---|---|
| `JustReadValueDouble` | [`JustReadValue`](../../aspect/JustReadValue.md) (via `ReadValueDouble`) | Pure narrowing via `ReadValueDouble`; no new members. |
| `ReadValueDouble` | [`ReadValue`](../../aspect/ReadValue.md) (via `ReadValueComparable<Double>`) | **Adds** `getF()` — null-safe boxed `Float` view of the held value. |
| `ReadDependencyDouble` | [`ReadDependency`](../../aspect/combinations/ReadDependency.md) | **Adds** arithmetic operators (`plus`/`minus`/`times`/`over`) with reactive and scalar overloads, `negative`/`negativeRO`/`inverse`/`inverseRO`, `min`/`max`, `round`, `readOnly`, `overridable`. All RO variants (default). |
| `ReadListenValueDouble` | [`ReadListenValue`](../../aspect/combinations/ReadListenValue.md) | Narrows buffer family (`buffer`/`validBuffer`/`weakBuffer` / `rateLimited` + builders) to `SealDouble`/`IndependentDouble`. No new arithmetic. |
| `ReadListenDependencyDouble` | [`ReadListenDependency`](../../aspect/combinations/ReadListenDependency.md) | **Adds** `fallback(Double)`. No other new members. |
| `WriteValueDouble` | [`WriteValue`](../../aspect/WriteValue.md) | **Adds** `setZero`/`setOne`/`setPositiveInfinte`/`setNegativeInfinite`/`setNull` write conveniences. |
| `WriteElsewhereDouble` | [`WriteElsewherePile`](../../aspect/combinations/WriteElsewherePile.md) | Entirely commented out (inert) — mirrors the dead generic deferred-write idea. |
| `ReadWriteValueDouble` | [`ReadWriteValue`](../../aspect/combinations/ReadWriteValue.md) | **Adds** `flip()` (sign negation); `setNull`. |
| `ReadWriteDependencyDouble` | [`ReadWriteDependency`](../../aspect/combinations/ReadWriteDependency.md) | **Overrides** `negative`/`inverse` to RW variants (write-back through); adds `negativeRW`/`inverseRW`, scalar+reactive `plus`/`minus`/`times`/`over` RW overloads, `setNull`. |
| `ReadWriteListenValueDouble` | [`ReadWriteListenValue`](../../aspect/combinations/ReadWriteListenValue.md) | Narrows the **writable** buffer family to `SealDouble`/`IndependentDouble`; adds `setNull`. |
| `ReadWriteListenDependencyDouble` | [`ReadWriteListenDependency`](../../aspect/combinations/ReadWriteListenDependency.md) | Full non-recompute double contract; `fallback`, `setNull`, resolves diamond. |
| `LastValueRemembererDouble` | [`LastValueRememberer`](../../aspect/LastValueRememberer.md) | Pure narrowing to `Double`; no new members. |

`PileDouble` (the double capstone, in the parent package) extends `ReadWriteListenDependencyDouble` and adds recompute/transaction/transform/seal — exactly as generic `Pile` sits atop `ReadWriteListenDependency`.

## The double-specific surface (what's callable where)

### Primitive-adjacent read accessor — `ReadValueDouble`
- `getF()` — null-safe downcast to `Float`: returns `null` when `get()` is `null`, otherwise `ret.floatValue()`. Useful for APIs requiring `Float`. No loss-of-precision checks.

### Read-side arithmetic and math operators — `ReadDependencyDouble` (all delegate to `PileDouble`)

All operators return [`SealDouble`](../SealDouble.md) (a read-only reactive wrapper). These are the **per-instance entry points**; the actual reactive combination lives in `PileDouble`.

- Arithmetic: `plus(op2)`/`minus(op2)`/`times(op2)`/`over(op2)` — two overloads each: reactive `ReadDependency<? extends Number>` and scalar `double`. Also explicit RO aliases `plusRO`/`minusRO`/`timesRO`/`overRO` (scalar only).
- Unary: `negative()` / `negativeRO()` — arithmetic negation. `inverse()` / `inverseRO()` — multiplicative inverse (1/x).
- Clamping: `min(op2)` / `max(op2)` — two overloads each (reactive and scalar `double`).
- Coercion: `round()` — returns a `SealInt` rounding via `Math.round`.
- Wiring: `readOnly()` — wraps in a read-only `SealDouble`; `overridable()` — builds a `PileDoubleImpl` that recomputes from this and accepts override writes.

### Read-side RW overrides — `ReadWriteDependencyDouble`
When a value also supports writing, `negative()` and `inverse()` are **overridden** to the write-back (`RW`) variants so that writing the negated/inverted result propagates the appropriate value back to the original:
- `negative()` / `negativeRW()` → `PileDouble.negativeRW`
- `inverse()` / `inverseRW()` → `PileDouble.inverseRW`

Scalar arithmetic operators (`plus`/`minus`/`times`/`over` / `plusRW`/`minusRW`/`timesRW`/`overRW`) also gain RW overloads accepting a reactive `ReadListenDependency<? extends Number>` second operand.

### Write conveniences
- `WriteValueDouble`: `setZero()`/`setOne()`/`setPositiveInfinte()`/`setNegativeInfinite()` — set well-known constants. Note the typo in `setPositiveInfinte` (missing 'i').
- `WriteValueDouble` / `ReadWriteValueDouble` / `ReadWriteListenValueDouble` / `ReadWriteDependencyDouble` / `ReadWriteListenDependencyDouble`: `setNull()` — chains `set(null)` returning `this`.
- `ReadWriteValueDouble`: `flip()` — negates the sign; silently no-ops when value is `null`.
- `ReadListenDependencyDouble` / `ReadWriteListenDependencyDouble`: `fallback(Double v)` — constant fallback while invalid; writes redirect to the original.

## Caveats & gotchas
- **`null` is a first-class state.** `getF()` returns `null`; arithmetic operators in `PileDouble` propagate `null` (reactive behavior — see `PileDouble.md`). `flip()` no-ops on `null`. Always account for the three-way nature (valid non-null / invalid / null).
- **RO vs RW operator split.** `ReadDependencyDouble.negative()` gives a read-only derived value; if your value is also writable, call the method via an `ReadWriteDependencyDouble` reference to get the write-back-capable version. The Java interface diamond resolution handles this for concrete types automatically.
- **Operators are entry points, not logic.** The interfaces only delegate; semantics (null handling, overflow, write-back routing) live in [`PileDouble`](../PileDouble.md).
- **`WriteElsewhereDouble` is inert** — wholly commented out, present only to mirror the dead generic deferred-write combination.
- **`setPositiveInfinte` is a typo** (missing second 'i') in `WriteValueDouble`. Do not fix without checking all call sites.

## Common tasks
- Read the current value as `float` → `getF()`.
- Build a reactive sum/difference/product/quotient → `a.plus(b)`, `a.minus(b)`, etc. (or `PileDouble.add(a,b)` statically).
- Clamp to a range → `a.min(upper).max(lower)` (chain two operators).
- Get a reactive integer rounding → `a.round()`.
- Negate or invert with write-back → use `negative()` / `inverse()` via an `ReadWriteDependencyDouble` reference.
- Buffer for UI stability → `a.buffer()`, `a.validBuffer()`, `a.rateLimited(cold, cool)`.
- Set a well-known constant value → `setZero()` / `setOne()` / `setPositiveInfinte()` / `setNegativeInfinite()`.

## Tech debt / warts
- `WriteElsewhereDouble` is dead commented-out code (mirrors the generic `WriteElsewherePile` vestige).
- `setPositiveInfinte` has a typo (missing second 'i'); mirrors `WriteValueDouble`.
- The `Comparable` layer (`ReadValueComparable`, `ReadDependencyComparable`, etc.) sits between the generic aspect interfaces and these `*Double` interfaces; it is not separately documented here. Its behavior is identical to the generic layer for ordering purposes.
