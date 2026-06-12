# `pile.specialized_bool.ConstantBool`

`ConstantBool` is `Constant<Boolean>` narrowed to `Boolean`, adding boolean-typed covariant returns and a constant-valued `not()`.

Source folder: `src`. File: `pile/specialized_bool/ConstantBool.java`.

Up: [bool index](_index.md) · [overview](../../overview.md). Generic base: [`../impl/Constant.md`](../impl/Constant.md). Operator algebra: [`PileBool.md`](PileBool.md).

## What it specializes

`ConstantBool extends Constant<Boolean> implements ReadWriteListenDependencyBool`. It is the boolean concretisation of the generic constant — **all reactive semantics (never-changing, always valid, silent writes, no listeners)** are unchanged from [`Constant`](../impl/Constant.md). Read that doc for the full behavior contract.

## Added / narrowed members

| Member | Delta |
|---|---|
| `setNull()` | Returns `ConstantBool` (covariant). Is a silent no-op (`return this`), matching `Constant`'s silent-ignore idiom. |
| `setName(String)` | Returns `ConstantBool` (covariant). Is a silent no-op (`return this`) — constants are unnamed. |
| `not()` | Returns a `ReadWriteListenDependencyBool` constant by calling `threeWay(Piles.FALSE, Piles.TRUE, Piles.NULL_B)`. The inverse is itself a constant, never a reactive computation. |

`not()` is the one behaviorally interesting member: it delegates to `threeWay`, which returns one of the three pre-built boolean constant singletons (`Piles.FALSE`, `Piles.TRUE`, or `Piles.NULL_B`) matching the logical negation of the current value. No memoization is needed because the value never changes.

## Caveats & gotchas

- All writes (`set`, `setNull`) are **silently ignored**, as with the generic `Constant`. A caller holding a `WriteValue<Boolean>` reference gets no error when writing to a `ConstantBool`.
- `setName` is a no-op — naming a constant has no effect.
- The `not()` result is also a constant; assigning / listening to it behaves exactly as any other `Constant`.

## Related

- [`../impl/Constant.md`](../impl/Constant.md) — full behavior contract (silent writes, always valid, no listeners).
- [`_index.md`](_index.md) — the specialization pattern and the whole `*Bool` family.
- [`PileBool.md`](PileBool.md) — the boolean operator algebra.
- [`combinations/_index.md`](combinations/_index.md) — `ReadWriteListenDependencyBool` and the combination interfaces.
