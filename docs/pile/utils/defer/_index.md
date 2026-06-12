# `pile.utils.defer` — package index (Tier 1)

Source folder: `src`.

The **deferral mechanism**: collect work and run it later (e.g. at the end of a transaction, or off the current thread) instead of immediately. Used to defer listener notifications and other side effects to a safe point.

Up: [utils index](../_index.md) · [overview](../../../overview.md).

## Types
- [`Deferrer`](Deferrer.md) — the interface for deferring a `Runnable` to a later flush point (run work later / at a safe point instead of immediately); the package's central contract. A nesting **suppression counter** decides defer-vs-run-now; dedup/ordering is the queue's job, not the deferrer's.
- [`DefererImpl`](DefererImpl.md) — the standard `Deferrer` implementation: a counter-gated queue runner (note the spelling: one `r`). Not thread-safe unless wrapped via `makeSynchronized`.
- [`DeferrerQueue`](DeferrerQueue.md) — the pluggable queue of pending `Runnable`s backing a deferrer; pick `FiFo`, `LiFo`, or `Dedup` ordering.
- [`ThreadLocalDeferrer`](ThreadLocalDeferrer.md) — a per-thread `Deferrer`: a `ThreadLocal<Deferrer>` holder that forwards every call to the current thread's own deferrer.
