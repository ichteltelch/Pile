# `pile.specialized_bool.PileBoolImpl`

`PileBoolImpl` is `PileImpl<Boolean>` narrowed to `Boolean` — the default full reactive boolean with recomputation, dynamic dependencies, and transactions.

Source folder: `src`. File: `pile/specialized_bool/PileBoolImpl.java`.

Up: [bool index](_index.md) · [overview](../../overview.md). Generic base: [`../impl/PileImpl.md`](../impl/PileImpl.md). Operator algebra and static gates: [`PileBool.md`](PileBool.md).

## What it specializes

`PileBoolImpl extends PileImpl<Boolean> implements PileBool`. All reactive semantics — recomputation, dynamic dependencies, validity, transactions, sealing, brackets, the corrector chain — are inherited unchanged from [`PileImpl`](../impl/PileImpl.md). Read that doc for the full behavior contract.

The `PileBool` interface adds the **boolean operator algebra** (`not`, `and`, `or`, `choose`, comparisons, etc.) as `default` instance methods and static factories — documented in full in [`PileBool.md`](PileBool.md).

## Added / narrowed members

| Member | Delta |
|---|---|
| `setName(String)` | Assigns `avName` directly; returns `PileBoolImpl` (covariant). |
| `setNull()` | Calls `set(null)`, returns `PileBoolImpl` (covariant). |
| `not()` | **Memoized** — double-checked-locked cache on `mutex`, same pattern as `IndependentBool.not()`. Note: does **not** use `withoutRecomputation` (unlike `IndependentBool`). |

## `not()` — memoization detail

`PileBoolImpl.not()` caches the inverse view in a volatile `ReadWriteListenDependencyBool not` field. On first call it invokes `PileBool.super.not()` (the `default` implementation, which builds a `SealBool` inverter via the static factory), then stores and returns it under `mutex`. Subsequent calls return the cached reference.

Unlike `IndependentBool.not()`, it does **not** wrap the construction in `Recomputations.withoutRecomputation()`. This means calling `not()` for the first time from within a dynamic recomputation will register the `SealBool` construction's reads as dynamic dependencies on the enclosing `PileBoolImpl`'s recomputer. In practice `not()` is typically called during wiring (not inside a recompute), so this is usually harmless — but it is the subtle difference from the `IndependentBool` variant.

## Where to look for everything else

- **Full reactive behavior** (recompute gates, transaction lifecycle, dependency protocol, brackets): [`../impl/PileImpl.md`](../impl/PileImpl.md).
- **All boolean instance operators** (`a.and(b)`, `a.or(b)`, `a.not()`, `a.choose(…)`, etc.) and the static gate catalogue: [`PileBool.md`](PileBool.md).
- **Builder entry point:** `PileBool.rb()` returns a `PileBuilder<PileBoolImpl>` pre-configured with `naturalOrder()` ordering.

## Related

- [`../impl/PileImpl.md`](../impl/PileImpl.md) — the ~3,600-line base; all reactive machinery lives here.
- [`PileBool.md`](PileBool.md) — the `PileBool` interface and operator algebra; also covers `PileBoolImpl` in its own section.
- [`SealBool.md`](SealBool.md) — the type returned by most `PileBool` static gates; what `not()` returns.
- [`_index.md`](_index.md) — family overview and the specialization pattern.
- [`combinations/_index.md`](combinations/_index.md) — `ReadWriteListenDependencyBool` and related combination interfaces.
