# `pile.specialized_int` — package index (Tier 1, delta)

The `int`/`Integer` member of the primitive-specialization family; **structurally identical to the exemplar [`specialized_bool`](../specialized_bool/_index.md)** — read that index first for the pattern ("generality in, specialization out", inheritance-based narrowing, memoized derived operators, silent-ignore/no-op idioms). This file records **only the int deltas**.

Source folder: `src` (all classes below).

Up: [overview](../../overview.md). Generic counterparts: [`../impl/PileImpl.md`](../impl/PileImpl.md). Aspects: [`../aspect/_index.md`](../aspect/_index.md). The pattern reference: [`../specialized_bool/_index.md`](../specialized_bool/_index.md) and its capstone [`../specialized_bool/PileBool.md`](../specialized_bool/PileBool.md).

## Same shape as bool

Confirmed structurally identical to the bool family: it **has both `MutInt` and `SuppressInt`** (the two optional members that exist only for `bool`/`int`, not `String`/`Comparable`), and a `combinations/` of the same 12 `*Int` interfaces. Inheritance, narrowed return types, and reactive semantics are all exactly as the bool index documents — nothing is repeated here.

## Concrete types — each maps to one generic counterpart (same as bool)

| `*Int` type | extends (generic) | role |
|---|---|---|
| `PileIntImpl` | [`PileImpl`](../impl/PileImpl.md) | default full reactive integer; implements `PileInt`. |
| `SealInt` | `SealPile<Integer>` | the sealable integer; the value type every redirecting operator factory on `PileInt` hands back. |
| `ConstantInt` | `Constant` | never-changing integer; always valid, silently ignores writes. |
| `IndependentInt` | `Independent` | always-valid, non-recomputing leaf integer. |
| `MutInt` | *(none)* | bare mutable `int` box (field `val`); **not a graph node**. Implements `JustReadValueInt` + `IntSupplier`; the only place `getAsInt()` lives (returns the raw `int`). Analogue of `MutBool`/`MutRef`. |
| `SuppressInt` | `IndependentInt` | reference-counted suppression-style flag value (int dual of `SuppressBool`). |

All per-type notes from the bool index (constants ignore `setNull`/`setName`; `Independent*` memoizes derived ops; `Seal*` is the redirect target; `Mut*`/`Suppress*` rationale) carry over unchanged.

## Int-specific surface — the delta that matters

The operators live on `PileInt` (static factories) and on `ReadDependencyInt` (instance `default`s that delegate to those factories). Where bool has `not`/`and`/`or`, int has arithmetic + comparisons. Default-handling rule throughout: **any `null` operand makes the result `null`** (the operator lambdas short-circuit on `null`).

### Arithmetic (returns `SealInt`)
On `ReadDependencyInt` as fluent instance methods (operand-2 may be a reactive `Integer`, an `int` constant, or — see "cross-type" — a `double`):
- `plus` / `minus` / `times` → `PileInt.add` / `subtract` / `multiply`
- `integerDivide` (truncating `/`), `remainder` (`%`), `modulo` (least non-negative residue: `c<0?c+b:c`)
- `negative()` / `negativeRO()` → `PileInt.negativeRO`; the writable `negativeRW` (and `addRW`/`subtractRW`) seal a **bijection** so writing the result writes the inverse back into the operand (uses `Bijection.define`/`Bijection.involution` and `bijectToInt`).
- `min` / `max` → `PileInt.min`/`max` (via `Math.min`/`max`).
- static-only: `add`/`subtract`/`multiply`/`integerDivide`/`remainder`/`modulo`/`min`/`max` in reactive×reactive, reactive×const, and const×reactive arities; plus `binOp` (the generic `ToIntBiFunction`/`Integer`-`BiFunction` constructor).

### Comparison → `bool` (returns `SealBool`)
These are **inherited from [`ReadDependencyComparable`](../specialized_Comparable/_index.md)**, not declared on `ReadDependencyInt`: `lessThan` / `greaterThan` / `lessThanOrEqual` / `greaterThanOrEqual` (reactive op2) and their `*Const` variants (constant op2), each taking a `Boolean nullIsLess` tristate (`null` ⇒ result is `null`). They route through `PileBool.lessThan`/`greaterThan`/… and hand back a `SealBool`. Int adds sign predicates of its own as static factories on `PileInt`: `isPositive`/`isNegative`/`isNonPositive`/`isNonNegative`/`isZero`/`isNonZero` (each `mapToBool`, `null`→`null`).

### Cross-type promotions
`PileInt` is unusual in eagerly promoting to **double**: `toDouble()`, `over(...)` (true division → `SealDouble` via `PileDouble.divide`), and `plus`/`minus`/`times`/`min`/`max` overloads taking a `ReadDependencyDouble` or `double` all return `SealDouble`. There is no `int` division operator named `div`/`over` — integer division is `integerDivide`; `over` is always floating-point. `mapToDouble`/`mapToBool` are the underlying mapping hooks (inherited).

### `choose` (sign-dispatch, int-only flavour)
`ReadDependencyInt` carries a four-way `choose`/`chooseWritable`/`chooseConst` keyed on the operand's **sign and nullity** (`ifNeg`/`ifZero`/`ifPos`/`ifNull`) — the int analogue of bool's two-way `choose`. Typed return variants exist for every family (`chooseInt`/`chooseBool`/`chooseDouble`/`chooseString`). All delegate to `PileInt._choose`/`_chooseWritable`/`_chooseConst`.

### Primitive accessor
There is **no `getAsInt`/`getValidAsInt` on the reactive read interfaces** — reactive reads return boxed `Integer` via the inherited `get()`. `getAsInt()` exists **only on `MutInt`** (the non-reactive box). So "primitive accessor with default-on-invalid" is not part of the reactive int surface; use `get()` and handle `null` yourself.

## Routing & aggregation

Binary operators route through `Piles.makeBinOp(op1, op2, new SealInt(), op)`; const-operand operators go through `mapToInt`/`bijectToInt`. Reductions use `Piles.aggregate(monoid, items)` with `PileInt.IntAggregator` (an `AggregationMonoid<Integer, ReadListenDependencyInt>`): the four pre-built monoids `sumAggregator`(0)/`productAggregator`(1)/`minAggregator`(MAX_VALUE)/`maxAggregator`(MIN_VALUE) back the varargs/`Iterable` reducers `sum`/`product`/`min`/`max`. The monoid caches neutrals as the shared constants `Piles.NULL_I`/`ZERO_I`/`ONE_I`/`MAX_VALUE_I`/`MIN_VALUE_I`. `inject` wraps inputs via `readOnlyWrapperIdempotent` (returns the input itself if it is already a default-sealed `SealInt` or a `ConstantInt`). See [`../impl/Piles/aggregation.md`](../impl/Piles/aggregation.md). There is **no abs / clamp / bounds operator** in this package (despite the task's prompt); only `signum(value)` (`(int)Math.signum`) as an extra. No `mapToBool`/`mapToDouble` are int-specific — they are inherited mapping hooks.

## Builders & wrappers

Factory shorthands on `PileInt`: `rb()`/`sb()`/`ib()`/`ib(init)` build `PileIntImpl`/`SealInt`/`IndependentInt`, each pre-seeded with `Comparator.naturalOrder()` ordering. `readOnly()`/`readOnlyWrapper`/`readOnlyWrapperIdempotent` and `overridable()` (→ `PileIntImpl` named `*`) mirror the generic wrappers, narrowed to int.

## Companion docs

- `PileInt.md` *(to be written)* — the `PileInt` interface and full int operator algebra (the capstone, analogous to [`PileBool.md`](../specialized_bool/PileBool.md)).
- `combinations/_index.md` *(to be written)* — the 12 `*Int` combination interfaces; note most operator `default`s actually live on `ReadDependencyInt` and the inherited `ReadDependencyComparable`, while `ReadValueInt`/`ReadWriteValueInt` are nearly empty (the latter adds only `flip()` and a typed `setNull()`).

## Caveats & warts (int-specific)

- **`null` propagation everywhere** — every arithmetic op returns `null` if any operand is `null`; comparisons return `null` when `nullIsLess` is `null`. No NPE, but easy to get a silently-`null` result.
- **`over` is always floating-point** — `intA.over(intB)` is `SealDouble`, not integer division. Use `integerDivide` for `int` quotient.
- `getAsInt` is **not** on reactive values, only on `MutInt`.
- `integerDivide`/`remainder`/`modulo` by a zero operand will throw `ArithmeticException` inside the recompute lambda (unguarded), as plain Java division would.
</content>
</invoke>
