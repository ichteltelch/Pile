# `IndependentInt` — always-valid, non-recomputing leaf integer

Thin int specialization of [`Independent`](../impl/Independent.md). An `IndependentInt` is a mutable integer value that is **always valid** and never recomputes — it is a reactive leaf (source node). All reactive semantics, invalidation behavior (silently ignored), and the prohibition on recomputation are inherited from `Independent`; read that doc for the full contract.

Source folder: `src`. Package: `pile.specialized_int`.

Up: [int index](_index.md) · [overview](../../overview.md). Generic base: [`Independent.md`](../impl/Independent.md). Family exemplar: [`../specialized_bool/_index.md`](../specialized_bool/_index.md).

## Class hierarchy

`IndependentInt extends IndependentComparable<Integer> implements ReadWriteListenDependencyInt`

`IndependentComparable<Integer>` sits between `Independent<Integer>` and `IndependentInt`; it installs natural-order comparator support. `IndependentInt` itself only adds typed covariant overrides.

## Delta over `Independent`

- **`setName(String)`** — covariant override; returns `this` (typed `IndependentInt`).
- **`setNull()`** — covariant override; calls `set(null)` and returns `this`.
- **`ReadWriteListenDependencyInt`** — the combination interface providing typed access and the int operator surface.
- Two commented-out `validBuffer`/`validBuffer_memo` overrides are present in the source — dead code, not yet removed.

## Common use

`IndependentInt` is the standard writable integer that acts as an input or user-controlled parameter in a reactive graph. Use `PileInt.ib()` / `PileInt.ib(initValue)` for the shorthand builder (pre-set with natural ordering).

## Caveats & gotchas

- **Always valid** — `allowInvalidation` calls are silently ignored (inherited from `Independent`); this is idiomatic, not a bug. Downstream values depending on an `IndependentInt` will never go invalid because of it.
- **No recomputation** — attempting to set a recompute function on an `IndependentInt` is not supported; the reactive graph treats it as a pure source.
- **`getAsInt()` not on the reactive interface** — reads return boxed `Integer` via `get()`; the primitive accessor lives only on `MutInt`.
- **Operator memoization** — like `IndependentBool`, derived operators (e.g., `negative()`) may be memoized on first call under a mutex. Repeated calls return the same derived reactive value.
