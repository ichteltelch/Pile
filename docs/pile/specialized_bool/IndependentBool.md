# `pile.specialized_bool.IndependentBool`

`IndependentBool` is `Independent<Boolean>` narrowed to `Boolean`, adding covariant typed returns and a memoized `not()`.

Source folder: `src`. File: `pile/specialized_bool/IndependentBool.java`.

Up: [bool index](_index.md) · [overview](../../overview.md). Generic base: [`../impl/Independent.md`](../impl/Independent.md). Operator algebra: [`PileBool.md`](PileBool.md).

## What it specializes

`IndependentBool extends Independent<Boolean> implements ReadWriteListenDependencyBool`. All reactive semantics — always valid, no dependencies, no recomputation, sealing, correctors, remember-last-value, brackets — are inherited unchanged from [`Independent`](../impl/Independent.md). Read that doc for the full behavior contract.

## Added / narrowed members

| Member | Delta |
|---|---|
| `setName(String)` | Calls `super.setName(name)` and returns `IndependentBool` (covariant). |
| `setNull()` | Calls `set(null)` and returns `IndependentBool` (covariant). |
| `not()` | Memoized: built once under `Recomputations.withoutRecomputation()`, cached in a volatile field with double-checked locking on `mutex`. |

## `not()` — memoization detail

The first call to `not()` invokes `ReadWriteListenDependencyBool.super.not()` (the `default` implementation in the combination interface) inside a `Recomputations.withoutRecomputation()` block, then stores the result in `not` under `mutex`. Subsequent calls return the cached value without constructing anything new. The TODO comment in the source notes a possible future switch to an `IdentityMemoCache`.

The `withoutRecomputation` block ensures the derivation of the inverse does not accidentally register a dynamic dependency on the enclosing recomputation (if `not()` is called from within a recomputer).

## Caveats & gotchas

- `not()` creates the inverse value lazily on first call; the result is the same object on every subsequent call.
- `setNull()` routes through `set(null)`, so correctors and the sealing interceptor apply normally. It does **not** shortcut to a field write.
- All other behavior — staying valid during a transaction, sealing semantics, corrector chains, remember-last-value — is exactly as documented in [`Independent`](../impl/Independent.md).

## Related

- [`../impl/Independent.md`](../impl/Independent.md) — full behavior contract.
- [`SuppressBool.md`](SuppressBool.md) — extends `IndependentBool`; adds reference-counted suppression semantics.
- [`_index.md`](_index.md) — the specialization pattern.
- [`PileBool.md`](PileBool.md) — the boolean operator algebra (default `not()` the memoization wraps).
- [`combinations/_index.md`](combinations/_index.md) — `ReadWriteListenDependencyBool`.
