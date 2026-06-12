# `ReadWriteListenDependencyDouble`

The full non-recompute double combination interface — unifies `ReadWriteListenValueDouble`, `ReadListenDependencyDouble`, and `ReadWriteDependencyDouble`; adds `fallback(Double)` and `setNull`.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`ReadWriteListenDependencyDouble extends ReadWriteListenValueDouble, ReadListenDependencyDouble, ReadWriteDependencyDouble, ReadWriteListenDependencyComparable<Double>`.

This is the top of the double combination lattice below `PileDouble`. Any value that can be read, written, listened to, and participates in the dependency graph implements this interface.

## What it adds

- `fallback(Double v)` → `Piles.fallback(this, v)` — a `SealDouble` that holds `v` while `this` is invalid; writes to the fallback redirect to `this`.
- `setNull()` — narrows return to `ReadWriteListenDependencyDouble`.

## Diamond resolution

Multiple supertypes declare `negative()`, `inverse()`, `setNull()`, and the buffer family. Java resolves these at the concrete type (`PileDouble`, etc.). No explicit disambiguation method is needed here (unlike the bool analogue which adds an explicit resolver for `not()`), because the `ReadWriteDependencyDouble` override of `negative`/`inverse` is unambiguous.

## See also
- [ReadWriteDependencyDouble](ReadWriteDependencyDouble.md) — RW arithmetic operators
- [ReadListenDependencyDouble](ReadListenDependencyDouble.md) — read-side `fallback`
- [ReadWriteListenValueDouble](ReadWriteListenValueDouble.md) — writable buffers
- [PileDouble](../PileDouble.md) — the double capstone extending this interface
- [combinations index](_index.md)
