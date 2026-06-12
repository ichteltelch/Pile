# `pile.impl.PileImpl`

Source folder: `src`. File: `pile/impl/PileImpl.java` (~3,614 lines).

The **default implementation of [`Pile`](../aspect/combinations/Pile.md)** — `PileImpl<E> extends `[`AbstractReadListenDependency`](AbstractReadListenDependency.md)`<E> implements Pile<E>, HasAssociations.Mixin`. Self-described as *"not very efficient, but OK for a few hundred Piles reacting at interactive speeds"*. The transaction/recompute *model* lives in [concepts/transactions.md](../../concepts/transactions.md); this doc maps the class. ARLD owns the shared machinery; `PileImpl` adds the value/validity representation, recomputation, dependency-change propagation, and transform.

## State model
- **Value:** `__value`, `valid` (volatile), `observedValid`. `get` returns `null` (not an exception) when invalid or destroyed.
- **Old value:** `oldValue`, `oldValid` — the pre-transaction snapshot used for change detection / restore.
- **Dependencies:** `_thisDependsOn`; and under `invalidDependenciesMutex`: `invalidDependencies`, `changingDependencies`, `changedDependencies`. `essentialDependencies`.
- **Transaction sub-counters:** `recomputationTransactions`, `dependencyTransactions`, `transformTransactions` — roll into ARLD's `openTransactions`. `ongoingRecomputation`. `invalidated` (manual-invalidation flag, ).
- **Recompute:** `recompute` (the `Recomputer`, ), `correctors`, `lazyValidating`, `autoValidationSuppressors`.
- **Reactive sub-values** (lazy `IndependentBool`s mirroring state): `validity`, `computing`, `validNull`, `autoValidating`.

## Method groups
- **Reads** — `get`, `getValid`/`getOldIfInvalid`/`getValidOrThrow`/`getValid(timeout)`, `isValid`/`isValidNull`/`isDestroyed`.
- **Dependency management** — `addDependency`/`addDependency0`, `removeDependency`, `setDependencyEssential`/`isEssential`, `allDependenciesValid`, `dependsOn`/`getDependencies`.
- **Recomputation** — the gate `__startPendingRecompute` → `___startPendingRecompute_undeferred` (; the `if (__openTransactions > recomputationTransactions …) return` gate is ); `_setRecompute`, `__scheduleRecomputation`, `cancelPendingRecomputation`, `autoValidate`, `revalidate`, `permaInvalidate`, `suppressAutoValidation`, `lazyValidate`/`setLazyValidating`.
  - **`MyRecomputation`** (inner class, ) — the [`Recomputation`](../aspect/recompute) implementation handed to recompute code: `fulfill`/`fulfillInvalid`/`fulfillRestoreOldValue`, `forgetOldValue`, dynamic-dependency recording (`activateDynamicDependencies`/`recordDependency`/`diffRecorded`, ), thread transfer, cancellation. Holds the outer `PileImpl` **weakly** (`WeakCleanupWithRunnable`, ).
- **Dependency-change propagation** — `dependencyBeginsChanging`, `escalateDependencyChange`, `dependencyEndsChanging`, `__dependencyIsNowValid`. (The begin/end bracket that drives the diamond — see [transactions.md](../../concepts/transactions.md).)
- **Transactions** (override ARLD) — `__beginTransaction`, `transaction`, `__endTransaction`.
- **Writing** — `set` (; returns the actually-stored value after corrections), `setNull`, `applyCorrection`/`_addCorrector`, `__conditionalSecretSet` (, used by transform).
- **Brackets / validity** — `openBrackets`/`closeBrackets`/`openOldBrackets`/`closeOldBrackets`, `moveValueToOldValue`/`copyValueToOldValue`, `__restoreValueFromOldValue`.
- **Transform** — `beginTransformTransaction`/`endTransformTransaction`, `runTransform`/`runTransformRevalidate`, `checkForTransformEnd`, `_setTransformHandler`/`getTransformHandler`. (Rudimentary — see README caveats.)
- **Lifecycle** — `destroy` (; also destroys dependers for which it's an essential dependency), `deepDestroy`.
- **Associations** — `HasAssociations.Mixin` accessors.

## Salient / surprising behavior
- **`get` never throws for invalid/destroyed — it returns `null`**. Use `getValidOrThrow`/`isValid` when you need to distinguish.
- **`isSealed` always returns `false`** — only [`SealPile`] can actually seal; `PileImpl` is never sealed.
- **The `_single`/`__double` underscore convention:** `_setRecompute`/`_setEquivalence`/`_addCorrector` are advanced configuration; `__beginTransaction`/`__scheduleRecomputation`/`__startPendingRecompute` are framework-internal protocol — normal users go through [`Piles`](Piles/_index.md)/builders, not these.
- **`MyRecomputation` holds the `PileImpl` weakly**, so an abandoned recomputation doesn't pin the value; cancellation is wired to GC cleanup.

## Caveats & gotchas
- Built for flexibility/debuggability, not speed.
- Mutex discipline is contractual (asserts only): recomputation/inform-queue work runs *without* `mutex`; state mutation runs *with* it.
- Setting while dependencies are invalid is allowed and interacts with the deep-revalidate registry — see [transactions.md § the subtle part](../../concepts/transactions.md).

## Related
- [`AbstractReadListenDependency`](AbstractReadListenDependency.md) — the base (shared machinery). · [concepts/transactions.md](../../concepts/transactions.md) — the model. · [`Pile`](../aspect/combinations/Pile.md) — the interface. · `SealPile` *(doc pending)* — the sealable subclass.
