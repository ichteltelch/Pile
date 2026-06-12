# `pile.impl` — package index (Tier 1)

Source folder: `src` (all classes below).

The **concrete implementations**: the general-purpose reactive-value classes, their shared base, the composite/list family, mutable-reference boxes, and the static utility hub. These implement the [combinations](../aspect/combinations/_index.md) and [aspects](../aspect/_index.md).

Up: [overview](../../overview.md). Model behind recompute/transactions: [concepts/transactions.md](../../concepts/transactions.md). Suspected bugs: [possible-bugs.md](../../possible-bugs.md).

## Shared base
- [`AbstractReadListenDependency`](AbstractReadListenDependency.md) (ARLD) — the base of `PileImpl` and `Independent`; holds the transaction counter, the `informQueue`, the brackets infrastructure, listeners, the deep-revalidate registry, and the abstract hooks subclasses fill in.
- [`AbstractReadListenDependency_NoDepender`](AbstractReadListenDependency_NoDepender.md) — ARLD variant for non-`Depender`, non-recomputing values (base of `Independent`); stubs the auto-validate/pending-recompute hooks to no-ops. *(`Constant` does NOT extend this — it implements `ReadWriteListenDependency` directly.)*

## General-purpose reactive values
- [`PileImpl`](PileImpl.md) — the default full [`Pile`](../aspect/combinations/Pile.md): recomputation, dynamic dependencies, transactions, transform, brackets. ~3,600 lines.
- [`SealPile`](SealPile.md) — `PileImpl` + `Sealable` (the only sealable Pile): overrides every structural mutator to throw when sealed, routes `set` through the interceptor, and bypasses the seal via a privileged `WriteDepender` proxy.
- [`Constant`](Constant.md) — never-changing value: always valid, never fires, silently ignores writes. **Standalone** — implements `ReadWriteListenDependency` directly (extends nothing).
- [`Independent`](Independent.md) — always-valid, no-dependency, non-recomputing leaf; `Sealable`; canonical impl of correctors, remember-last-value, brackets. Stays valid during a transaction (unlike `PileImpl`).

## Utility hub
- [`Piles`](Piles/_index.md) — the static utility catalogue (~100 type-agnostic factories/combinators + aggregation monoids + deep-revalidate helpers). **Index live.**

## Composite / list family
- [`PileCompound`](PileCompound.md) — abstract base bundling a subclass's component `PileImpl`s behind one dependency-aggregating `Hub` `head` (carries a dummy value; you observe its *change*, not its value).
- [`AbstractValueList`](AbstractValueList.md) — `PileCompound` specialized to a dynamic ordered list of `ReadWriteListenDependency` cells feeding the head; subclasses fill `wrap`/interval hooks.
- [`PileList`](PileList.md) — the minimal `AbstractValueList`; holds **pre-built reactive boxes** and does NOT auto-wrap raw values (value-based `add`/`set` throw `UnsupportedOperationException` — use `addV`/`setV`).
- [`Hub`](Hub.md) — a `PileImpl<Object>` that fires on **any** dependency change (always-unequal equivalence; informationless fixed value). `set` throws — use `setExplicitly`.

## Mutable-reference boxes
- [`MutRef`](MutRef.md) — plain mutable get/set cell (`ReadWriteValue` + `JustReadValue`), not a graph dependency, always valid; used pervasively as a closure/out-param box.
- [`ReleasableMutRef`](ReleasableMutRef.md) — abstract base of the boxes: public `val` field + `get` + an abstract `release`; a bare `Supplier`.
- [`EarlyMutRef`](EarlyMutRef.md) — `Prosumer`-only box for use very early at startup (avoids class-loading `Piles` and the aspect graph that `MutRef` drags in).

## Debug
- [`DebugCallback`](DebugCallback.md) — debug-only hook interface for tracing one value's lifecycle (set/fulfill/transaction/recompute/invalidate events); `DebugEnabled.DE`-gated, installed via `_setDebugCallback`.
- [`TransactionTracker`](TransactionTracker.md) — debug-only record of *why* a transaction is open (originator + reason); inspect `PileImpl._transactionReasons` in the debugger.
