# `pile.impl.Independent`

A reactive value with no dependencies and no concept of invalidity — always valid, never recomputes; `Sealable`, and the **canonical implementation of correctors, remember-last-value, and brackets** for non-recomputing values.

Source folder: `src`. File: `pile/impl/Independent.java` (~700 lines).

`Independent<E>` is the simple, leaf reactive cell: you hand it a mandatory initial value and `set(...)` it directly (unless sealed). It extends [`AbstractReadListenDependency_NoDepender`](AbstractReadListenDependency_NoDepender.md) (an [ARLD](AbstractReadListenDependency.md) variant for values that are not `Depender`s) and implements `ReadWriteListenDependency`, [`Sealable`](../aspect/Sealable.md), [`CorrigibleValue`](../aspect/CorrigibleValue.md), [`RemembersLastValue`](../aspect/RemembersLastValue.md), `HasAssociations.Mixin`, and `HasInfluencers`. Contrast its sibling [`PileImpl`](PileImpl.md), the full recomputing `Pile`.

Up: [impl index](_index.md) · [overview](../../overview.md). Transaction model: [concepts/transactions.md](../../concepts/transactions.md).

## What it's for

A value that is **its own source of truth**: it has no `Dependency`s, never schedules a recomputation, and `isValid` is hard-coded `true`. All the "always-valid" query methods collapse to a plain read: `getValid(...)`, `getValidOrThrow`, `getOldIfInvalid`, `getAsync` all return `value`, and `validity` returns the shared constant `Piles.TRUE`. Because it never goes invalid, it is the natural place to put the reusable machinery the recomputing piles also need but that doesn't depend on recomputation: corrector chains, remember-last-value gating, sealing, and the value/old-value bracket dance.

It is the implementation behind `Piles.independent(...)` / the `IndependentBuilder` family; `Constant` and `SealPile` cover the never-changing and sealable-recomputing cases respectively.

## Key methods by purpose

- **Writing** — `set(val)` routes through the seal interceptor if sealed, else `set0`. `set0` applies correctors, short-circuits on equivalence, then opens a transaction and swaps the value inside the mutex with bracket close/open. `setNull`/`setter`-via-`makeSetter` build on it.
- **Sealing** — `seal` / `seal(interceptor, allowInvalidation)`, `isSealed`/`isDefaultSealed`, `makeSetter` for the privileged bypass.
- **Correctors** — `_addCorrector`, `applyCorrection`; lazily-allocated list (`lazyInitCorrectors`, ).
- **Remember-last-value** — `remembersLastValue`, `suppressRememberLastValue`, `storeLastValueNow`/`resetToLastValue`.
- **Old-value / brackets / transactions** — `moveValueToOldValue`/`__restoreValueFromOldValue`/`copyValueToOldValue` and the `__valid`/`__oldValid`/`__value`/`__oldValue` hooks ARLD calls.
- **Lifecycle** — `destroy`, `follow(leader, alsoInvalidate)`, `nullOrInvalid`.

This doc is a **delta over the javadoc** — the per-method contracts live in the source and in the aspect docs linked above. Below is what those don't say.

## Salient / surprising behavior

- **Stays VALID during a transaction.** Unlike `PileImpl` (which goes invalid while a transaction is open and may restore the pre-transaction value), `Independent` reports `isValid`/`__valid`/`isValidAsync`/`observedValid` as `true` unconditionally. `set0` opens a transaction only to batch listener firing and to drive the old-value/bracket bookkeeping, not to model invalidity. `__hasChangedDependencies` is always `false` and `__recomputerTransactions` is `0`, so the ARLD transaction-close logic never tries to recompute or to "restore on no-change".
- **No recomputation, by construction.** `__scheduleRecomputation`, `__startPendingRecompute` (in the `_NoDepender` base), `revalidate`, `lazyValidate`, `setLazyValidating` are all no-ops; `__ongoingRecomputation`/`__dependencies` return `null`; `isComputing`/`isLazyValidating` are `false`.
- **`permaInvalidate` is a silent no-op** — an `Independent` cannot be invalidated (the throwing variant is commented out). The `makeSetter` `WriteValue`'s `permaInvalidate`/`revalidate` are likewise empty.
- **`set0` short-circuits via `equivalence`** before opening the transaction; an equivalent write fires nothing. The actual field swap only happens when `value != val` by *identity*, inside the mutex with `closeBrackets`/`openBrackets` around it.
- **`set0` fires `fireDeepRevalidate` on every real write** — `Independent` participates in the deep-revalidate registry as a dependency even though it is never itself invalid.
- **`nullOrInvalid` builds a sealed `IndependentBool` companion** named `"<name> == null"`, updated via the `informQueue` (not synchronously) by `setIsNullValue` wired in `set0`. It is lazily created, keeps `this` strong, and is itself sealed.
- **`willNeverChange` ⇔ default-sealed and no setter handed out** — same shape as `SealPile`'s but without the recomputer/dependency clause (there are none).
- **`follow(leader, ...)` adds a _weak_ value listener** and `keepStrong(vl)` to pin it; the `Independent` tracks the leader via `transferFrom`.
- **Construction guard:** if `DebugEnabled.ERROR_ON_CREATE_IN_DYNAMIC_RECOMPUTATION` is on, constructing an `Independent` inside an active dynamic dependency recording throws.

## Sealing (delta vs. `Sealable`/`SealPile`)

`Independent` has its **own** seal implementation, parallel to `SealPile`'s (the [`Sealable`](../aspect/Sealable.md) doc cites `SealPile` line numbers; the contract is identical, the lines differ):

- `sealed` is a `volatile Consumer<? super E>`; `null` means unsealed. The default throw interceptor's message is *"Cannot call set directly on a sealed **Independent** value"* — the `SealPile` doc's quoted *"…sealed SealableValue"* message is `SealPile`'s; both modes (throw/ignore/warn/redirect) work the same way here.
- Re-sealing with the same interceptor is a no-op; with a different one it throws.
- Sealing freezes structure: `_addCorrector`, `_setEquivalence`, and non–value-only bracket changes (`_addValueBracket`/`_addOldValueBracket`/`_addAnyValueBracket`, ) throw `IllegalStateException` when sealed. `makeSetter` throws if already sealed.
- `destroy` clears `sealed=null` first so teardown can proceed, mirroring `SealPile`.

## Remember-last-value (this is the canonical impl)

See [`RemembersLastValue`](../aspect/RemembersLastValue.md) — `Independent` is the reference implementation it points to. The gate is `storingSuppressors`: `remembersLastValue` ⇔ `storingSuppressors <= 0`, and `suppressRememberLastValue` increments/decrements it via a `Suppressor.wrap`. `storeLastValueNow`/`resetToLastValue` look up the `LastValueRememberer` association and no-op if absent. The auto-store listener and the restore-at-construction wiring live **not here but in the builder** — see the DEFER note below and the superdoc's [Remember/restore lifecycle](../aspect/RemembersLastValue.md).

## Correctors (this is the canonical impl)

See [`CorrigibleValue`](../aspect/CorrigibleValue.md). `applyCorrection` threads the value left-to-right through the lazily-allocated `correctors` list, synchronizing on the list during iteration. **Veto handling diverges from `PileImpl`:** `set0`'s catch merely `printStackTrace`s a `VetoException` and returns `get` — it does **not** honor `VetoException.revalidate`, whereas `PileImpl` does. Any other `RuntimeException` is logged at `SEVERE` and the write dropped. This divergence is already recorded in the `CorrigibleValue` doc and is expected, not a bug.

## Old value & brackets

`Independent` carries `oldValue` + an `oldValid` flag. `moveValueToOldValue` and `copyValueToOldValue` (, which just delegates to `moveValueToOldValue`) capture the current value into the old slot under the mutex with old-bracket open/close; `__restoreValueFromOldValue` swaps it back. All of these increment `ListenValue.DEFER` suppressors around the mutex section so listeners fire after the lock is released. `closeOldBrackets` nulls `oldValue` when the brackets aren't kept.

## Caveats & gotchas

- **`set` returns the actually-stored value, never blindly the argument.** When sealed it returns `get`; on an equivalent write it returns the existing `value`; on a veto it returns `get`. Always use the return value.
- **The `Independent` veto path ignores `revalidate`** — a corrector that throws `new VetoException(true, …)` on an `Independent` will not trigger a recompute (there is nothing to recompute), unlike on a `PileImpl`.
- **Field swap is identity-gated** (`value != val`, ) even though the earlier short-circuit uses `equivalence`. Two `equivalence`-equal but non-identical objects already returned early, so this is consistent — but a custom `equivalence` that is *coarser* than identity means a "different" object that is `equivalence`-equal is dropped before reaching the swap.
- **`nullOrInvalid` updates asynchronously** via the `informQueue`, so the companion boolean lags the value within a transaction; don't treat it as synchronously consistent.
- **Sealing is one-shot and structure-freezing** (correctors, equivalence, non-value-only brackets) — configure everything before sealing, and grab `makeSetter` first if you need a private write channel.
- **Logger name typo:** the logger is registered as `"Indepednent"`.
- **`set0` catches `VetoException` and `RuntimeException` separately but not `Error`** — an `Error` from a corrector propagates out of `set` (consistent with `PileImpl`, noted for completeness).

## Common tasks (how to…)

- **Make a settable leaf value:** `Piles.independent(initial)…build` (this class), then `v.set(x)`.
- **Make it read-only after setup:** grab `WriteValue<E> w = v.makeSetter` *before* `v.seal`; afterwards only `w.set(...)` works.
- **Clamp / veto writes:** install correctors via the builder (`.bounds`, `.neverNull`) — see [`CorrigibleValue`](../aspect/CorrigibleValue.md).
- **Persist a user setting:** build with `fromStore(...)`; gate programmatic writes with `suppressRememberLastValue` — see [`RemembersLastValue`](../aspect/RemembersLastValue.md).
- **Mirror another value:** `v.follow(leader, alsoInvalidate)`.
- **Track null-ness reactively:** `v.nullOrInvalid` returns a sealed boolean (for an `Independent` "invalid" never occurs, so it is effectively "is null").

## Tech debt / warts

- **Duplicated seal implementation.** `Independent` and `SealPile` each carry their own `sealed`/`defaultInterceptor`/`seal`/`makeSetter` machinery rather than sharing one — two copies of the same contract to keep in sync.
- **Veto handling differs from `PileImpl`** (stack-trace-and-ignore vs. honor `revalidate`, ) — already logged against `CorrigibleValue`.
- **Logger name misspelled** `"Indepednent"`.
- **`__beginTransaction(boolean b)` ignores its argument** and always calls `super.beginTransaction(true)`; the `b` parameter is dead.
- **Save/restore wiring is off-class.** The remember/restore lifecycle and auto-store listener live in `AbstractIndependentBuilder` (undocumented), not in `Independent` — see DEFER note.

## DEFER note

`Independent`'s **save/restore wiring** (the `LastValueRememberer` association, restore-at-construction, and the auto-store value listener) is assembled in `AbstractIndependentBuilder.build`, a large undocumented builder. Per task instructions it was **not** deep-read; only the contract surface it touches on `Independent` (the association lookup in `storeLastValueNow`/`resetToLastValue`, the `storingSuppressors` gate) is documented here. The lifecycle is described from the superdoc in [`RemembersLastValue` § Remember/restore lifecycle](../aspect/RemembersLastValue.md).

## Related

- [`AbstractReadListenDependency`](AbstractReadListenDependency.md) — the shared base (transactions, inform-queue, brackets, listeners). · [`AbstractReadListenDependency_NoDepender`](AbstractReadListenDependency_NoDepender.md) — the no-`Depender` variant `Independent` extends.
- [`PileImpl`](PileImpl.md) — the recomputing sibling (goes invalid in a transaction; `Independent` does not).
- Aspects: [`Sealable`](../aspect/Sealable.md) · [`CorrigibleValue`](../aspect/CorrigibleValue.md) · [`RemembersLastValue`](../aspect/RemembersLastValue.md).
- Concepts: [transactions.md](../../concepts/transactions.md). · [overview.md](../../overview.md).
