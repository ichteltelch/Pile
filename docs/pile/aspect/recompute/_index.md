# `pile.aspect.recompute` — package index (Tier 1)

Source folder: `src` (all types below).

The **recomputation machinery**: the contract for the code that recomputes a [`Pile`](../combinations/Pile.md)'s value, the handle that code is given, the dynamic-dependency recorder, and the static helpers. The general-purpose implementation of the handle is `PileImpl.MyRecomputation` ([PileImpl](../../impl/PileImpl.md)); the recompute/transaction *model* is in [concepts/transactions.md](../../../concepts/transactions.md).

Up: [aspect index](../_index.md) · [overview](../../../overview.md). Builders configure recomputation via [`IPileBuilder`](../../builder/IPileBuilder.md).

## Types
- [`Recomputer`](Recomputer.md) — the functional interface for user-supplied recompute code (`accept(Recomputation)`), plus policy hooks (dependency-scouting, may-remove-dynamic-dependency).
- [`Recomputation`](Recomputation.md) — the handle handed to recompute code to drive an in-progress recomputation: fulfill/fulfill-invalid/restore-old-value, query & record dependencies, thread transfer, cancellation (impl: `PileImpl.MyRecomputation`).
- [`Recomputations`](Recomputations.md) — static helpers: current-recomputation/recorder thread-locals, scouting query, suspend dependency-recording vs. recompute-starts, and the `NOT_NOW` listener-deferral suppressor.
- [`DependencyRecorder`](DependencyRecorder.md) — interface that records reads of `Dependency`s during *scouting* (dynamic-dependency discovery), routing each read to the active `Recomputation`.
- [`GenericDependencyRecorder`](GenericDependencyRecorder.md) — the concrete `DependencyRecorder`: collects recorded dependencies in a list and optionally chains each on to a wrapped (outer) recorder.
