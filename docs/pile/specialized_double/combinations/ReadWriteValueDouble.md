# `ReadWriteValueDouble`

Combines `ReadValueDouble` and `WriteValueDouble` and adds `flip()` — sign negation of the held value.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`ReadWriteValueDouble extends ReadValueDouble, WriteValueDouble, ReadWriteValueComparable<Double>`.

## What it adds

- `flip()` — sets the value to its arithmetic negation (`-v`). **Silently no-ops when the value is `null`** (idiomatic null handling in Pile, not a bug).
- `setNull()` — `set(null)`, returns `this` (narrows to `ReadWriteValueDouble` for chaining).

## Caveats
- `flip()` on `null` is a silent no-op — no exception, no log. This is the Pile idiom for null values.
- Note: `flip()` here means **sign negation** (multiply by −1), not a boolean toggle as in the bool analogue.

## See also
- [WriteValueDouble](WriteValueDouble.md) — write conveniences
- [ReadWriteDependencyDouble](ReadWriteDependencyDouble.md) — adds reactive RW arithmetic operators
- [combinations index](_index.md)
