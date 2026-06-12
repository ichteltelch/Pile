# `pile.specialized_bool.combinations` — boolean-specialized combination interfaces (Tier 1 map)

Twelve `*Bool` interfaces that mirror the twelve generic [`pile.aspect.combinations`](../../aspect/combinations/_index.md) interfaces exactly, narrowed to `Boolean`, and adding a boolean-specific surface (primitive accessors + logic operators as default methods).

Source folder: `src` (all interfaces below).

Up: [bool package index](../_index.md) *(pending)* · [overview](../../../overview.md). Capstone concrete value: [`PileBool`](../PileBool.md) *(pending)*. Generic counterparts: [aspect combinations index](../../aspect/combinations/_index.md).

## What these interfaces are
Each `*Bool` interface is a **thin assembly interface**: it unions its generic combination interface(s) (re-typed to `Boolean`) and, where useful, contributes `boolean`-specific **default methods**. The substantive boolean machinery — the logic operators, the `choose*` family, `not`/`invertIf` — lives in [`PileBool`](../PileBool.md) (routing through `Piles.aggregate`); the interfaces below are the **per-instance entry points** that delegate to it. Return types are narrowed to the boolean-specialized concrete types ([`SealBool`](../SealBool.md), [`IndependentBool`](../IndependentBool.md), `PileBoolImpl`, [`SealInt`](../../specialized_int/SealInt.md), `SealDouble`, `SealString`) so callers stay in the primitive-specialized world without casts.

The 4-dimension lattice (Read / Write / Listen / Dependency) and the capstone role of `Pile` are explained in the [generic combinations index](../../aspect/combinations/_index.md); the same shape holds here, with `PileBool` as the boolean capstone.

## Map: `*Bool` → generic counterpart
| `*Bool` interface | Mirrors (generic) | Narrows / adds |
|---|---|---|
| `JustReadValueBool` | [`JustReadValue`](../../aspect/JustReadValue.md) (+ `ReadValueBool`) | `@FunctionalInterface`; pure read of a `Boolean`. No new members beyond `ReadValueBool`. |
| `ReadValueBool` | [`ReadValue`](../../aspect/ReadValue.md) (+ `BooleanSupplier`) | **Adds** the primitive read accessors and `threeWay`/`getAsBoolean` (see below). The operator-free base of the boolean read surface. |
| `ReadDependencyBool` | [`ReadDependency`](../../aspect/combinations/ReadDependency.md) | **Adds** the read-side logic operators (`and`/`or`/`not`/…), the whole `choose*` factory family, `mapToInt`, `readOnly`, `overridable`, `validIfTrue`. |
| `ReadListenValueBool` | [`ReadListenValue`](../../aspect/combinations/ReadListenValue.md) | Narrows the buffer family (`buffer`/`validBuffer`/`weakBuffer`/`rateLimited` + builders) to `SealBool`/`IndependentBool`. No new logic. |
| `ReadListenDependencyBool` | [`ReadListenDependency`](../../aspect/combinations/ReadListenDependency.md) | **Adds** `fallback(Boolean)` and `whileTrueRepeat(...)`; narrows `setName`. Return type of `notRO` (from `ReadDependencyBool`). |
| `WriteValueBool` | [`WriteValue`](../../aspect/WriteValue.md) | **Adds** write conveniences `setTrue`/`setFalse`. |
| `WriteElsewhereBool` | [`WriteElsewherePile`](../../aspect/combinations/WriteElsewherePile.md) | Entirely commented out (inert), mirroring the dead generic `WriteElsewhere*` idea. |
| `ReadWriteValueBool` | [`ReadWriteValue`](../../aspect/combinations/ReadWriteValue.md) | **Adds** `flip()` (toggle; no-op on `null`). |
| `ReadWriteDependencyBool` | [`ReadWriteDependency`](../../aspect/combinations/ReadWriteDependency.md) | **Adds** the writable `not`/`notRW`, `invertIf(control)` (controlled write-back), `setNull()`. |
| `ReadWriteListenValueBool` | [`ReadWriteListenValue`](../../aspect/combinations/ReadWriteListenValue.md) | Narrows the **writable** buffer family to `SealBool`/`IndependentBool`; adds `setNull()`. No new logic. |
| `ReadWriteListenDependencyBool` | [`ReadWriteListenDependency`](../../aspect/combinations/ReadWriteListenDependency.md) | The full non-recompute boolean contract; `fallback`, `setNull`, resolves the `not()` diamond to `ReadWriteDependencyBool.not()`. |
| `LastValueRemembererBool` | [`LastValueRememberer`](../../aspect/LastValueRememberer.md) | Pure narrowing to `Boolean`; no new members. |

`PileBool` (the boolean capstone, in the parent package) extends `ReadWriteListenDependencyBool` and adds recompute/transaction/transform/seal — exactly as generic `Pile` sits atop `ReadWriteListenDependency`.

## The boolean-specific surface (what's callable where)

### Primitive read accessors — `ReadValueBool`
- `isTrue()` / `isFalse()` — `Boolean.TRUE`/`FALSE`-equality on `get()`, so a `null` value is **neither** true nor false (both return `false`). This is the null-safe idiom; there is no NPE-on-unbox.
- `getAsBoolean()` — implements `BooleanSupplier`; identical to `isTrue()` (treats `null` as `false`).
- `threeWay(ifTrue, ifFalse, ifNull)` — non-reactive 3-way select on the current value; the reactive counterpart is `chooseConst(...)` on `ReadDependencyBool`.
- Static `isTrue(Supplier)` / `isFalse(Supplier)` — the same null-safe test against any `Supplier<? extends Boolean>`.

(There is no `getValidOrFalse`-style accessor declared here; validity-aware reads come from the generic `ReadValue`/buffer machinery.)

### Read-side operators — `ReadDependencyBool` (default methods, each delegates to `PileBool`)
These are the **per-instance entry points** to the operators that [`PileBool`](../PileBool.md) also exposes statically; PileBool routes the actual combination via `Piles.aggregate` (covered in PileBool.md — not duplicated here).
- Logic: `and(op2)`, `andScd(op2)` (short-circuit/2nd variant), `or(op2)`, `orScd(op2)`, `not()` / `notRO()` (read-only inversion). All return [`SealBool`](../SealBool.md) (or `ReadListenDependencyBool` for `not`).
- Reactive select: the `choose*` family — `choose`, `chooseWritable`, `chooseConst`, plus per-type specializations `chooseInt`/`chooseBool`/`chooseDouble`/`chooseString` (and `chooseWritable*`/`chooseConst*` variants), all funneling through `_choose` / `_chooseWritable` / `_chooseConst` with a `SealPile` template. `chooseConstV`/`chooseVConst` mix a constant with a reactive arm.
- Coercion / wiring: `mapToInt()` (`true`→1, else→0, via `__BOOL_TO_INT`), `readOnly()`, `overridable()`, `validIfTrue()` (a `Dependency` that is valid only while this holds `true`).

### Write-side operators (`ReadWriteDependencyBool`)
- `not()` / `notRW()` — writable inversion (delegates to `PileBool.notRW`); writing the result writes back through.
- `invertIf(control)` — controlled NOT (`PileBool.cNot`): writes to the result attempt an appropriate write-back to `this`.

### Write conveniences
- `WriteValueBool`: `setTrue()` / `setFalse()`.
- `ReadWriteValueBool`: `flip()` — toggles; **silently returns** (no-op) when the value is `null` (idiomatic null handling, not a bug).
- `setNull()` (on `ReadWriteDependencyBool`, `ReadWriteListenValueBool`, `ReadWriteListenDependencyBool`) — `set(null)` returning `this` for chaining.
- `ReadListenDependencyBool` / `ReadWriteListenDependencyBool`: `fallback(Boolean)` — a value that takes the constant while this is invalid, writes redirected back to this.
- `ReadListenDependencyBool`: `whileTrueRepeat(intervalMillis[, mayInterrupt, scheduler], job)` — repeatedly runs `job` while this is `true`; returns a `Pile` wrapping the `ScheduledFuture` (invalidate/suppress to pause, destroy to stop).

## Caveats & gotchas
- **`null` is a first-class third state.** `isTrue`/`isFalse`/`getAsBoolean` all treat `null` as "not true and not false"; `flip()` no-ops on `null`; the `choose(ifTrue, ifFalse)` 2-arg forms map `null` to `null` (or `constNull()`). Always account for the three-way nature.
- **Operators are entry points, not logic.** The interfaces only delegate; semantics (short-circuit vs strict, write-back routing, aggregation) live in [`PileBool`](../PileBool.md).
- **`not()` is overloaded by capability.** Read-only contexts (`ReadDependencyBool`) give `PileBool.notRO`; read-write contexts (`ReadWriteDependencyBool`) give `PileBool.notRW`; `ReadWriteListenDependencyBool` explicitly disambiguates the diamond to the read-write version.
- **`WriteElsewhereBool` is inert** — wholly commented out, present only to mirror the dead generic deferred-write combination.

## Common tasks
- Test a reactive boolean's current state without NPE → `isTrue()` / `isFalse()` / `getAsBoolean()`.
- Build a derived reactive AND/OR/NOT → `a.and(b)`, `a.or(b)`, `a.not()` (or `PileBool.and(a,b)` statically).
- Pick between reactive values on a condition → `cond.choose(ifTrue, ifFalse[, ifNull])` (or `chooseConst` for plain values, `chooseWritable` for two-way).
- One-shot mutate → `setTrue()` / `setFalse()` / `flip()` / `setNull()`.
- Run something repeatedly while a flag is true → `whileTrueRepeat(...)`.

## Tech debt / warts
- `WriteElsewhereBool` is dead commented-out code (mirrors the generic `WriteElsewherePile` vestige).
- `andScd`/`orScd` names are terse ("Scd" = the alternate strict/second-argument variant); intent lives only in `PileBool`'s javadoc.
