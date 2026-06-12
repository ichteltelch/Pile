# `StrongDependencyBracket`

A [`ValueOnlyBracket`](_index.md) that, while open, makes one or more [`Depender`](../Depender.md)s depend on a [`Dependency`](../Dependency.md) extracted from the held value — holding the dependers by **strong reference**.

Source folder: `src`. File: `pile/aspect/bracket/StrongDependencyBracket.java`.

This is the strong-reference variant of the dependency-bracket pair; see its sibling [`WeakDependencyBracket`](WeakDependencyBracket.md) and the related [`DependerBracket`](DependerBracket.md). For the open/close lifecycle and the `mutex`-held caveat that governs all brackets, see the package [`_index.md`](_index.md) and the [overview](../../../overview.md).

## What it's for

Pull a value into a dependency graph **for exactly as long as the holding value carries it**. When the bracket opens on a plain value `v`, it computes `Dependency fd = extract.apply(v)` and calls `addDependency(fd, recompute)` on every configured depender; when it closes, it calls `removeDependency(fd, recompute)` on the same set. The `extract` function maps the held plain value to the dependency the dependers should wire to (often `fd == v` when the value itself is a `Dependency`, but `extract` lets you reach a sub-value).

Construction is via the static factory `create(...)` (the constructor is package-private), which wraps the result in `detectStuck` when `DebugEnabled.DETECT_STUCK_BRACKETS` is on.

## Key fields / behavior

- `extract` — **must be deterministic.** It is applied independently at open *and* at close; if it returns a different `Dependency` the second time, the wrong dependency is removed and the original is leaked into the graph (javadoc on the constructor, ).
- `d0` + `ds[]` — the dependers. `d0` may be `null`; `ds` may be `null`; the array is defensively cloned in the constructor. Each is wired/unwired in `doOpen`/`doClose`.
- `recompute` — passed straight through to `add/removeDependency`, telling each depender whether to trigger a recomputation on the wiring change.
- `value == null` short-circuits: `open` returns `true` (treated as held) and `close` returns `false`, doing nothing in either case.

## Salient / surprising behavior

- **Lock-aware deferral.** If the `owner` is a [`HasInternalLock`](../HasInternalLock.md) that currently holds its lock, the actual wiring is deferred via `ListenValue.DEFER.run(...)` rather than done inline. This avoids mutating the dependency graph reentrantly while the value's mutex is held (the bracket-package caveat). Otherwise it runs synchronously.
- **Never a no-op, never obsolete.** `openIsNop`/`closeIsNop` return `false` and `canBecomeObsolete` returns `false`. The bracket always has an effect and never asks to be removed from its host.

## How it differs from `WeakDependencyBracket`

This is the crux. Both classes are structurally near-identical; the difference is reference strength and its GC consequences.

| | `StrongDependencyBracket` | [`WeakDependencyBracket`](WeakDependencyBracket.md) |
|---|---|---|
| Holds dependers via | **strong** field references (`Depender d0`, `Depender[] ds`) | `WeakReference<Depender>` |
| GC of dependers while open | **prevented** — the bracket keeps them alive as long as it lives | dependers may be collected; cleared refs are skipped |
| `recompute` flag | yes, threaded through `add/removeDependency(fd, recompute)` | no — uses the single-arg `addDependency(fd)` / `removeDependency(fd)` (default recompute) |
| `canBecomeObsolete` | `false` | `true` — when every weak ref is cleared, the bracket reports itself obsolete so the host can drop it |
| Obsolescence bookkeeping | none | tracks an `obsoleteOn` map; if `doOpen` wired nothing (all refs dead) it marks the owner obsolete, and a subsequent `open` on that owner returns `false` |

Practical guidance: use **strong** when the dependency relationship must persist for the value's whole tenure and you accept that the bracket pins the dependers in memory. Use **weak** when the dependers have their own lifecycle and should be free to be collected — at the cost of the bracket silently becoming a no-op once they are gone.

### Versus `DependerBracket`

`DependerBracket` wires a dependency relationship for the lifetime of the held value as well; rely on its own [`DependerBracket.md`](DependerBracket.md) for the exact distinction (it differs in *which* side of the relationship the held value plays / how the depender-dependency roles are arranged), rather than re-deriving it here.

## Caveats & gotchas

- **`extract` determinism is a hard requirement**, not a nicety — a non-deterministic or value-mutated `extract` leaks dependencies (see above).
- The strong references mean an open `StrongDependencyBracket` is a **liveness anchor** for its dependers; a forgotten/stuck bracket keeps them (and their transitive graph) reachable. `DebugEnabled.DETECT_STUCK_BRACKETS` exists precisely to catch brackets that never close.
- Wiring may run **asynchronously** (deferred) relative to the `open`/`close` call when the owner holds its lock; do not assume the dependency edge exists the instant `open` returns.
- `null` value is silently treated as "nothing to do" — not an error.

## Tech debt / warts

- `StrongDependencyBracket` and `WeakDependencyBracket` are almost line-for-line duplicates differing only in reference strength + the obsolescence path; the shared open/close/deferral skeleton is copy-pasted rather than factored into a common base.
- Commented-out `QueuedValueBracket.getDefaultDependencyQueue.enqueue(...)` lines record an abandoned alternative to `ListenValue.DEFER` — dead breadcrumbs.
- `doClose` is `public` while `doOpen` is `private` — an inconsistent visibility slip with no apparent reason.
