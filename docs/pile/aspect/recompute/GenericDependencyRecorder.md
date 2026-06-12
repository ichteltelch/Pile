# `GenericDependencyRecorder`

The concrete [`DependencyRecorder`](DependencyRecorder.md) — collects every recorded `Dependency` in a list and optionally chains each one on to a wrapped ("outer") recorder.

Source folder: `src` · package `pile.aspect.recompute`.

Up: [recompute index](_index.md) · [overview](../../../overview.md). See [transactions.md](../../../concepts/transactions.md) for the recompute/scouting model.

## What it does

During *scouting* (dynamic-dependency discovery), reads of [`Dependency`](../Dependency.md) values are reported to the current `DependencyRecorder`. `GenericDependencyRecorder` is the standard implementation:

- **Records** — `recordDependency(d)` lazily allocates an `ArrayList` on first use and appends `d`. The backing field stays `null` until something is actually recorded, so a recorder that sees no reads allocates nothing.
- **Chains** — it holds an optional `outer` recorder passed to the constructor. After recording locally, `recordDependency` forwards the same `Dependency` to `outer.recordDependency(d)`. This lets nested scouting scopes both keep their own slice and feed the enclosing scope.
- **Links to a recomputation** — it has no `Recomputation` of its own. `getRecomputation` / `getReceivingRecomputation` simply delegate to `outer` (returning `null` if there is no outer). So the receiving recomputation is whatever the wrapped recorder reports.

## Reading the result

`getRecorded` returns the recorded dependencies as a `Set` — **deduplicated and unordered** despite the internal list. It is size-optimized: `emptySet` for none, `singleton(...)` for one, a fresh `HashSet` otherwise. The list preserves insertion order and duplicates internally, but callers only ever see the de-duplicated set.

## Who instantiates it

Not constructed by client code. The builders create one per scouting pass: [`ISealPileBuilder`](../../builder/ISealPileBuilder.md) wraps the active `Recomputation` (`reco`) in a recorder, installs it as the current recorder for the duration of the dependency-extracting call via `Recomputations.withDependencyRecorder(...)`, then reads `getRecorded`. Here the `Recomputation` itself acts as the `outer`, so discovered dependencies are both collected and registered on the recomputation.

## Caveats & gotchas

- **No `Recomputation` without an outer.** A recorder built with `outer == null` reports a `null` recomputation from both `getRecomputation` and `getReceivingRecomputation`. The class is essentially a collector that piggybacks on its outer for recomputation identity.
- **Set, not list.** Do not rely on `getRecorded` for order or multiplicity; that information is discarded on the way out even though it is kept internally.
- Not thread-safe: the lazy `record` allocation and `add` are unsynchronized. Each scouting pass uses its own instance, so this is fine in practice but the object is single-use / single-threaded by design.

## Tech debt / warts

- The internal `ArrayList` keeps order and duplicates that the only accessor (`getRecorded`) throws away — the ordered/duplicated form is never exposed, so the list could in principle be a `HashSet` from the start.
