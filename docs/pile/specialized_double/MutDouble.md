# `MutDouble`

Bare mutable `double` box — a closure/out-param helper, not a reactive graph node.

Source folder: `src`. Package: `pile.specialized_double`.

Up: [_index.md](_index.md) · [overview](../../overview.md). Generic analogue: [../impl/MutRef.md](../impl/MutRef.md). Bool/int parallels: `MutBool`, `MutInt` (same family).

## What it is

`MutDouble` is a plain mutable wrapper around a primitive `double` field `val`. It implements `JustReadValueDouble` (read-only functional combination, no listeners or dependency graph) and `DoubleSupplier`. It is **not** a reactive graph node — no invalidation, no listeners, no transactions. The intended uses are:

- **Accumulator in a lambda** — Java requires effectively-final captured variables; a `MutDouble` box is the standard workaround.
- **Out-parameter** — pass a `MutDouble` into a method so the method can write a `double` result back.
- **Memoized scratch** — inline computation where you want a named `double` with a fluent `.set(...)` return.

## Key methods (delta over the generic)

`MutDouble` is not a subclass of any generic reactive impl; these methods are specific to this class:

| Method | What it does |
|---|---|
| `set(double)` | Sets `val`, returns `this` |
| `set(MutDouble)` | Copies `o.val` into `this.val`, returns `this` |
| `get()` | Returns boxed `Double` (from `val`) |
| `getAsDouble()` | Returns primitive `val` (satisfies `DoubleSupplier`) |
| `sqrt()` | Returns `Math.sqrt(val)` |
| `toString()` | Returns `"<val>"` (angle-bracket enclosed) |

`sqrt()` and `getAsDouble()` are **only** on `MutDouble` — there is no reactive `sqrt` or primitive `getAsDouble` on the graph-node types.

## Caveats & gotchas

- `val` is `public` — reads and writes can bypass `get()`/`set()` entirely, which is intentional for performance in tight loops.
- No null support: `val` is a primitive; the boxed `get()` never returns `null`.
- No reactive semantics whatsoever. If you need a mutable double that participates in the dependency graph, use `IndependentDouble` instead.
- Default constructor initializes `val` to `0` (Java primitive default).
