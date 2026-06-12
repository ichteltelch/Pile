# `ConstantDouble`

Never-changing `double` reactive value; always valid, silently ignores writes.

Source folder: `src`. Package: `pile.specialized_double`.

Up: [_index.md](_index.md) · [overview](../../overview.md). Generic counterpart: [../impl/Constant.md](../impl/Constant.md).

## What it is

`ConstantDouble` is the `double`/`Double` specialization of `Constant<Double>`. It extends `ConstantComparable<Double>` and implements `ReadWriteListenDependencyDouble`, which is the full combination interface (read + write + listen + depend) narrowed to `Double`. Having the `Write` side despite being a constant follows the same pattern as the generic `Constant` — write calls are accepted but silently ignored (idiomatic, not a bug).

## Delta over the generic

The entire class body is:
- Constructor `ConstantDouble(Double init)` — delegates to `ConstantComparable`.
- `setNull()` override — returns `this` (no-op; a constant cannot become null via mutation).

No `setName`. No `not()`-equivalent (there is no `negativeConst` memoized here; constant negation would be another `ConstantDouble` created by the caller). All reactive semantics — always-valid, no listeners ever fire, write silently dropped — come from the generic `Constant` chain.

## Caveats & gotchas

- **Silent write ignore** is idiomatic. Calling `.set(...)` or `.accept(...)` on a `ConstantDouble` does nothing; no exception is thrown.
- The value IS-A `ReadWriteListenDependencyDouble`, so it can be passed anywhere a writable double is expected. Callers that care about whether the write had effect must use a different type.
- `null` is a legal init value; the resulting `ConstantDouble` is a constant-null double (always invalid / always `null`).
