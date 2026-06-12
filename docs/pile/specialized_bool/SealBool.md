# `pile.specialized_bool.SealBool`

`SealBool` is `SealPile<Boolean>` narrowed to `Boolean`; it is also the concrete type that `PileBool`'s redirecting `not(…)` and comparison factories return.

Source folder: `src`. File: `pile/specialized_bool/SealBool.java`.

Up: [bool index](_index.md) · [overview](../../overview.md). Generic base: [`../impl/SealPile.md`](../impl/SealPile.md). Operator algebra: [`PileBool.md`](PileBool.md).

## What it specializes

`SealBool extends SealPile<Boolean> implements ReadWriteListenDependencyBool, PileBool`. All reactive semantics — recomputation, dynamic dependencies, the seal mechanism, the privileged-depender bypass, structural-mutator guards — are inherited unchanged from [`SealPile`](../impl/SealPile.md). Read that doc for the full behavior contract and sealing concepts.

## Added / narrowed members

| Member | Delta |
|---|---|
| `setName(String)` | Assigns `avName` directly (same as `SealPile`'s own covariant `setName`); returns `SealBool`. |
| `setNull()` | Calls `set(null)` and returns `SealBool` (covariant). |

`SealBool` declares no new methods and no `not()` override — the `default not()` from `PileBool` / `ReadWriteListenDependencyBool` is used as-is (no memoization here, unlike `IndependentBool` / `PileBoolImpl`).

## Role in the bool algebra

`SealBool` is the **return type of the static `PileBool` gates** (`not(…)`, `and`, `or`, `equalTo`, comparisons, `choose`, etc.). When a gate builds a derived boolean via `SealPileBuilder`, the result is a `SealBool`. This means derived booleans are sealable and, where the builder wires a redirect, **writable back to the source** (the result of `not(rw)` is a `SealBool` whose seal interceptor writes the inverse back to `rw`). See [`PileBool.md`](PileBool.md) for the full gate catalog.

## Caveats & gotchas

- `SealBool` carries no new behavior; all surprises (sealed write-redirect, structural-mutator throws, the privileged depender, `willNeverChange`) live in [`SealPile`](../impl/SealPile.md).
- Being the gate return type means most `SealBool` instances in practice are **default-sealed** (read-only) from the moment of construction. Only the `*RW` / `chooseWritable` / `cNot` factories produce writable (redirect-sealed) instances.
- No `not()` memoization — repeated `x.not()` calls on a `SealBool` construct a new inverse each time (unlike `IndependentBool` and `PileBoolImpl`).

## Related

- [`../impl/SealPile.md`](../impl/SealPile.md) — full behavior contract (the seal mechanism, the privileged depender, structural guards).
- [`PileBool.md`](PileBool.md) — the static gates that produce `SealBool` values.
- [`_index.md`](_index.md) — the specialization pattern.
- [`combinations/_index.md`](combinations/_index.md) — `ReadWriteListenDependencyBool` and `PileBool`.
