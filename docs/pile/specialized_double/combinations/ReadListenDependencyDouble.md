# `ReadListenDependencyDouble`

Narrows [`ReadListenDependency`](../../aspect/combinations/ReadListenDependency.md) to `Double` and adds `fallback(Double)`.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`ReadListenDependencyDouble extends ReadListenDependencyComparable<Double>, ReadListenValueDouble, ReadDependencyDouble`.

## What it adds

- `fallback(Double v)` → `Piles.fallback(this, v)` — returns a `SealDouble` that holds `v` whenever `this` is invalid; writes to the fallback redirect to `this`.

This is the read-only fallback (no write-back from the constant into this). The writable variant is available via `ReadWriteListenDependencyDouble.fallback`.

## See also
- [ReadWriteListenDependencyDouble](ReadWriteListenDependencyDouble.md) — the full read-write version; also has `fallback`
- [ReadDependencyDouble](ReadDependencyDouble.md) · [ReadListenValueDouble](ReadListenValueDouble.md)
- [combinations index](_index.md)
