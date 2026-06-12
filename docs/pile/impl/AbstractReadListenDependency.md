# `pile.impl.AbstractReadListenDependency` (ARLD)

Source folder: `src`. File: `pile/impl/AbstractReadListenDependency.java` (~1,980 lines).

The shared base class that **holds most of the transaction logic common to [`PileImpl`](PileImpl.md) and `Independent`**. It implements [`ReadListenDependency`](../aspect/combinations/ReadListenDependency.md), `ListenValue.Managed`, and [`HasInternalLock`](../aspect/HasInternalLock.md), and communicates with its subclasses through a set of abstract `__`-prefixed hooks. The deep transaction/recompute *model* is in [concepts/transactions.md](../../concepts/transactions.md) — this doc is the structural map of the class.

## What it owns vs. delegates
ARLD owns the cross-cutting machinery; subclasses own the value/validity representation and recomputation.
- **Owns:** the transaction counter, the inform-queue, the mutex, listeners, brackets, the deep-revalidate registry, equivalence, debug metadata.
- **Delegates (abstract hooks):** what "valid"/"value"/"old value" mean, recomputation, change tracking. See Abstract hooks.

## Core state
- `mutex` — the one monitor guarding the object. **Lock discipline is pervasive:** many methods `assert Thread.holdsLock(mutex)` or `assert !Thread.holdsLock(mutex)`. The inform-queue runs *outside* the mutex on purpose.
- `openTransactions` — the private transaction counter; its openers are listed at `ARLD`. See [transactions.md](../../concepts/transactions.md).
- `dependOnThis` — the dependers, held as **`WeakIdentityCleanup` weak refs** so a depender can be GC'd when otherwise unreferenced.
- `informed` — dependers told this is "changing" but not yet "done changing"; **they keep a transaction open while it's changing**.
- `informQueue` — queued depender-notifications run outside `mutex`.
- `equivalence` — change-detection relation (default `DEFAULT_EQUIVALENCE`, `ARLD`); `_setEquivalence`/`_getEquivalence`.
- `avName` (debug name), `owner`, `dc` (`DebugCallback`), `creationTrace` — debug metadata; `dc`/`creationTrace` exist only when `DebugEnabled.DE`.

## Method groups

### Transactions (the heart)
`beginTransaction(workInformQueue, moveValueToOldValue, scout)` and `__endTransaction(changedIfOldInvalid)` move the counter, remember/restore the old value, and on close decide whether to start a recomputation, fire a change, or restore the pre-transaction value (`noChangedDependencies`, `ARLD`). `isInTransaction` / `inTransactionValue` expose the state. **Read [concepts/transactions.md](../../concepts/transactions.md) for the model** — don't re-derive it from here.

### Depender wiring & the inform-queue
`__addDepender` / `__removeDepender` register dependers (weakly) and enqueue `dependencyBeginsChanging`/`dependencyEndsChanging` notifications. `__workInformQueue(...)` drains the queue outside the mutex, with elaborate single-runner + deadlock-evasion logic (`someThreadIsWorkingInformQueue`).

### Brackets
`openBrackets`/`closeBrackets` (current value), `openOldBrackets`/`closeOldBrackets` (old value), and the `anyBrackets` that transfer between the two when value == oldValue. Registered via `_addValueBracket`/`_addOldValueBracket`/`_addAnyValueBracket`; inheritable ones flow via `bequeathBrackets`. **Brackets `open`/`close` run while `mutex` is held** — see the brackets doc (TODO) for what you must not do inside them.

### Deep-revalidate registry
The bookkeeping that distinguishes "valid despite invalid dependencies" cases: `dependersNeedingDeepRevalidate`, `thisNeedsDeepRevalidate`, `__dependerNeedsDeepRevalidate`/`__thisNeedsDeepRevalidate`, `fireDeepRevalidate` (, only fires while invalid), `suppressDeepRevalidation`. This is **orthogonal to transactions** — see [transactions.md § the subtle part](../../concepts/transactions.md).

### Observation & long-term invalidity
`_getListenerManager`/`fireValueChange`; listeners fire deferred via `ListenValue.DEFER`. `informLongTermInvalid` propagates *observed* invalidity to dependers and flips observed-validity to false (the actual-vs-observed-validity distinction from the README).

## Abstract hooks (subclasses fill these in)
ARLD calls these; `PileImpl`/`Independent` implement them: `moveValueToOldValue`/`copyValueToOldValue`, `isComputing`, `revalidate`, `__ongoingRecomputation`, `__recomputerTransactions`, `__clearChangedDependencies`, `__restoreValueFromOldValue`, `__hasChangedDependencies`, `__oldValid`/`__valid`/`__oldValue`/`__value`, `__shouldRemainInvalid`, `cancelPendingRecomputation`, `isAutoValidating`, `__startPendingRecompute`, `observedValid`/`__setValidity`, `__scheduleRecomputation`, `__dependencies`.

## Caveats & gotchas
- **Mutex discipline is contractual but unchecked** (asserts only). Inform-queue work and `__workInformQueue` must run *without* holding `mutex`; brackets and most state changes run *with* it.
- The weak `dependOnThis` set means dependers vanish on GC — intended, but a depender kept alive only by being a depender will be collected (see [`Dependency`](../aspect/Dependency.md) destructibility).
- `creationTrace`, `dc`, and the `_transactionReasons` tracking only exist under `DebugEnabled.DE`.

## Related
- [`PileImpl`](PileImpl.md) — the main subclass. · [concepts/transactions.md](../../concepts/transactions.md) — the model. · [`HasInternalLock`](../aspect/HasInternalLock.md), [`HasAssociations`](../aspect/HasAssociations.md).
