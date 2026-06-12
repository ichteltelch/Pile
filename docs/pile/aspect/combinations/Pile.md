# `Pile<E>`

The capstone combination interface and the library's namesake: a full reactive value bundling every aspect that makes a `PileImpl` — read/write/listen/dependency plus recomputation, transactions, transform, and sealing surface.

Source folder: `src` · package `pile.aspect.combinations` · interface `Pile<E>`.

Up: [aspect index](../_index.md) · [overview](../../../overview.md). The recompute/transaction **model** lives in [concepts/transactions.md](../../../concepts/transactions.md) — this doc points there rather than duplicating it.

## What it's for

`Pile<E>` is the one interface a general-purpose reactive value implements. It is purely an **assembly point**: it `extends` the combination contract [`ReadWriteListenDependency<E>`](ReadWriteListenDependency.md) and the granular aspects `WriteDepender`, [`CorrigibleValue`](../CorrigibleValue.md), [`HasAssociations`](../HasAssociations.md), `HasInfluencers`, [`LazyValidatable`](../LazyValidatable.md), [`CanAutoValidate`](../CanAutoValidate.md), `TransformableDependency`, and `AutoValidationSuppressible.Single` (Pile.java). On top of that union it adds a small set of methods that only a *fully-featured* value needs: recompute configuration, transaction-driven revalidation, transform/seal hooks, and debug plumbing.

The javadoc states the naming convention: a type-specialized variant for type `T` is named `Pile[T]` (e.g. `PileBool`, `PileInt`) and its implementation `Pile[T]Impl`; the general-purpose implementation is **`PileImpl`** (`pile.impl`, source folder `src`), where essentially all real behavior lives. This interface is almost entirely method declarations + a few defaults; **read `PileImpl` for semantics.**

## The `_single` / `__double` underscore convention

Method names here carry a deliberate visibility signal (it is a convention, not enforced):

- **No prefix** — ordinary public API (`set`, `setNull`, `isComputing`, `cancelPendingRecomputation`, `joinRecomputation`, `forgetOldValue`, `isSealed`, `deepRevalidate`).
- **`_` single underscore** — *configuration / advanced* methods you call deliberately but that are not part of everyday reading/writing: `_setRecompute`, `_setTransformHandler`, `_setEquivalence`, `_setDebugCallback`, `_setOwner`, `_isRecomputerDefined`, `_isRecomputationPendingOrOngoing`, `_printConstructionStackTrace`. Builders set most of these for you.
- **`__` double underscore** — *framework internals*: should only be called by the framework (or from a documented default), not by user code. Here that is `__fireDeepRevalidate` and the transaction primitives `__beginTransaction`/`__endTransaction` used by the `deepRevalidate` default (Pile.java). The transactions doc shows many more `__`-methods on `PileImpl`.

When scanning the API, read the prefix first: `__` means "don't call this unless you are the framework."

## Methods by purpose

### Recompute configuration
- `_setRecompute(Recomputer<E>)` — install the `Recomputer` that produces values (`PileImpl._setRecompute`, PileImpl.java).
- `_isRecomputerDefined` — whether one is set (PileImpl.java).
- `_setEquivalence(BiPredicate)` — the equivalence relation deciding "did the value actually change" on transaction close (see old-value remembering in [transactions](../../../concepts/transactions.md)).
- `_isRecomputationPendingOrOngoing` (PileImpl.java), `cancelPendingRecomputation(boolean cancelOngoing)` (PileImpl.java), `joinRecomputation` / `joinRecomputation(long)` (PileImpl.java) — observe / cancel / await the current recomputation. `joinRecomputation` delegates to `Recomputation#join`.
- `computing` / `isComputing` — reactive + snapshot views of "a recomputation is running" (PileImpl.java).
- `lazyRequest(boolean recordRead)` — **default here** (Pile.java): for lazy mode, asynchronously calls `get` (via `StandardExecutors.unlimited`) if invalid; optionally records the read.

### Transactions & revalidation
The transaction *model* is in [concepts/transactions.md](../../../concepts/transactions.md); client-facing API is on [`DoesTransactions`](../DoesTransactions.md). Added here:
- `deepRevalidate` — **default** that brackets `revalidate` + `__fireDeepRevalidate` in an internal `__beginTransaction/__endTransaction` pair (Pile.java). Cascades revalidation to dependers marked "need deep revalidation."
- `__fireDeepRevalidate` — internal; "should only be called from within `deepRevalidate`." Drives the deep-revalidate registry (PileImpl side: `deepRevalidate(Dependency)` PileImpl.java).
- `changedDependencies` — the set of dependencies that changed during the current/last transaction (PileImpl.java).
- `observedValid` — the "observed validity" snapshot **without** recording a dependency on `validity` or marking it observed (PileImpl.java). Contrast with reading `validity`.

### Transform
- `_setTransformHandler(TransformHandler<E>)` (PileImpl.java) and `setBehaviorDuringTransform(BehaviorDuringTransform)` (PileImpl.java). Transform is flagged immature in the overview; `TransformableDependency` is the aspect side.

### Seal
- `isSealed` — **on `PileImpl` always returns `false`** (PileImpl.java); only `SealPile` (which adds [`Sealable`](../Sealable.md)) can report `true`. Treat a plain `Pile` as unsealable.

### Lifecycle
- `set(E)` / `setNull` — write; `set` returns the *actually-set* value (correctors/sealing may alter it — see [`WriteValue`](../WriteValue.md)). `setNull` is a default that calls `set(null)` and returns `this` (Pile.java).
- `forgetOldValue` — drops the remembered pre-transaction old value by closing the "old" brackets under a deferred-listener guard (PileImpl.java).
- `destroyIfMarkedDisposable` — **default**: destroys iff `isMarkedDisposable` (Pile.java).
- `setName(String)` returns `Pile<E>` (covariant); `asDependency` returns `this` (a `Pile` is its own `Dependency`, Pile.java).

### Debug
- `_setDebugCallback(DebugCallback)` and `_printConstructionStackTrace` — **no-ops unless `DebugEnabled.DE` is true** (Pile.java). `_setOwner(Object)` sets a debug "parent/owner" that can also double as a GC keep-alive for a derivation source.

## Salient / surprising behavior

- **This interface is a map, not a mechanism.** Almost nothing here is implemented in the interface; the defaults that exist (`setNull`, `deepRevalidate`, `lazyRequest`, `destroyIfMarkedDisposable`, `validNull`, `asDependency`) are thin. Go to `PileImpl` for real semantics, and to [transactions.md](../../../concepts/transactions.md) for the recompute/transaction state machine.
- `observedValid` is intentionally side-effect-free w.r.t. dependency recording — use it in debug/inspection paths, not as a reactive read.
- `isSealed` is `false` for the common implementation; sealing is a `SealPile` concern.
- Debug methods silently do nothing in production builds (the `debug_off` source folder compiles `DE` to `false`; see [overview](../../../overview.md)).

## Caveats & gotchas

- `__`-prefixed methods (`__fireDeepRevalidate`, `__beginTransaction`, `__endTransaction`) are framework internals — calling them directly outside their documented pairings can corrupt the transaction counter. Prefer `deepRevalidate`, and use the `Suppressor` returned by `transaction` rather than the raw `__begin/__endTransaction` pair.
- `lazyRequest` fires an **asynchronous** `get`; the value will not be valid by the time the call returns.
- Naming is admittedly unsystematic (overview tech-debt note); the `_`/`__` prefixes are the most reliable signal of intended audience.

## Common tasks

- **Make a value recompute from a function** → build via `pile.builder` / `Piles` (which call `_setRecompute` for you); rarely call `_setRecompute` directly.
- **Batch several writes** → `transaction` on [`DoesTransactions`](../DoesTransactions.md); see [transactions.md](../../../concepts/transactions.md).
- **Force a whole subtree to revalidate after a manual set on an invalid dependency** → `deepRevalidate`.
- **Wait for an in-flight recompute** → `joinRecomputation` / `joinRecomputation(long)`; **cancel one** → `cancelPendingRecomputation(true)`.
- **Drop the remembered old value** → `forgetOldValue`.

## See also

- Implementation: `PileImpl` (`pile.impl`, `src`) — read it for actual behavior.
- Parent contract: [`ReadWriteListenDependency`](ReadWriteListenDependency.md) *(doc forthcoming)*.
- Aspects: [`DoesTransactions`](../DoesTransactions.md), [`Sealable`](../Sealable.md), [`ReadValue`](../ReadValue.md), [`WriteValue`](../WriteValue.md), [`Dependency`](../Dependency.md), [`CorrigibleValue`](../CorrigibleValue.md), [`CanAutoValidate`](../CanAutoValidate.md), [`LazyValidatable`](../LazyValidatable.md).
- Model: [concepts/transactions.md](../../../concepts/transactions.md).
