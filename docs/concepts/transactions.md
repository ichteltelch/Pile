# Transactions

Cross-cutting concept. Implemented in the `src` source folder across:
- `pile.aspect.DoesTransactions` — the client-facing aspect interface.
- `pile.impl.AbstractReadListenDependency` (ARLD) — the shared transaction counter and machinery (base of `PileImpl` and `Independent`).
- `pile.impl.PileImpl` — the `Pile` specialisation: typed sub-counters, the recompute gate, dependency-change propagation, and the deep-revalidate interaction.
- `pile.impl.TransactionTracker` — debug-only record of *why* a transaction is open.

All line references are `File.java:line` at the time of writing; treat them as signposts, not contracts.

## TL;DR

- A transaction is an **"this value is in flux"** state. While it is open, the value **does not recompute**, and the value it held at the start is remembered as the *old value* so change can be detected when the transaction closes.
- Transactions are opened **both internally** (a recomputation is pending/ongoing; a dependency is changing/invalid; a transform) **and by client code**.
- **Client-facing purpose:** batch several manual writes that the framework can't know belong together (e.g. the `x`,`y`,`z` of a point), so dependers recompute *once*, against a consistent snapshot, instead of on each intermediate write.
- **The diamond problem is NOT what client transactions are for.** Single-change consistency (`A→{R,S}→X`: A recomputes once) is handled automatically by the dependency/invalidation propagation, which itself uses internal transactions.

## Client-facing API (`pile.aspect.DoesTransactions`)

```java
Suppressor t = value.transaction;        // transaction(true): also invalidate for the duration
try { value.set(x); value.set2(y); … }      // batched writes; dependers don't react yet
finally { t.release; }                    // closing the Suppressor ends the transaction
```

- `transaction(boolean invalidate)` opens a transaction and returns a `Suppressor`; releasing it ends the transaction. `transaction` = `transaction(true)`.
- `invalidate` controls whether the value goes invalid for the transaction's duration *if it supports invalidity and no transaction was already open*. **`PileImpl` invalidates; `Independent` stays valid**.
- Low-level `__beginTransaction(boolean)` / `__endTransaction` exist for efficiency but you must pair them yourself (–); prefer the `Suppressor` form.
- Observe transaction state reactively via `isInTransaction` / `inTransactionValue` (ARLD).

**What a client needs to know ends here.** The rest is internal.

## Internal model

### One counter, several reasons
The state is a single per-value counter `openTransactions` (ARLD), moved by `beginTransaction(...)` (ARLD) and `__endTransaction(boolean)` (ARLD). Its documented openers (ARLD): a dependency is invalid · a recomputation is pending · a recomputation is ongoing · explicit `set` · explicit `permaInvalidate` · external `transaction`.

`PileImpl` keeps **typed sub-counters** that roll into it:
- `recomputationTransactions` (PileImpl) — a recomputation is pending ("pending recomputation") or running ("ongoing recomputation").
- `dependencyTransactions` (PileImpl) — a dependency is currently changing.
- `transformTransactions` (PileImpl) — a transform is in progress.
- Client/`set`/`invalidate`/`revalidate` each open one transiently.

When `DebugEnabled.DE` is on, every open transaction also has a `TransactionTracker` in `_transactionReasons` (PileImpl) recording originator + reason (+ optional stack trace), so the debugger can show exactly why a value is stuck in transaction.

### The recompute gate
A `Pile` may start recomputing **only when the only open transactions are its own recomputation's**:

> `if (__openTransactions > recomputationTransactions && !scout) → don't start` — PileImpl `__startPendingRecompute`.

So any client/dependency/transform transaction blocks recomputation. (Recomputation is *also* gated separately on `allDependenciesValid` and on `autoValidationSuppressors`/`lazyValidating`.)

### Old-value remembering
On opening the first transaction, the current value (if valid) is copied/moved to `oldValue` (ARLD; `moveValueToOldValue`/`copyValueToOldValue`). On close, `noChangedDependencies(...)` (ARLD) compares new vs. old via the value's equivalence to decide whether a change actually happened; if the value ended invalid with no changed dependencies, the old value is **restored** (`__restoreValueFromOldValue`, ARLD). This is why a transient invalidation that resolves to the same value emits no spurious change.

## How the diamond is handled (automatically)

A `Dependency` brackets each change with a begin/end pair sent to its dependers:
- `Depender.dependencyBeginsChanging(...)` (PileImpl) → `__beginTransaction`, adds the dep to `changingDependencies`, `dependencyTransactions++`.
- `Depender.dependencyEndsChanging(...)` (PileImpl) → `dependencyTransactions--`, `__endTransaction`; the dep then lands in (or out of) `invalidDependencies`.

This propagates transitively: a depender that just entered a transaction brackets *its own* dependers. In `A→{R,S}→X`, A holds an open transaction for each of R and S and cannot recompute until **both** close — so A recomputes once, with consistent inputs. No client transaction is involved.

## The subtle part: "valid despite invalid dependencies" + deep-revalidate

**An invalid dependency holds a transaction on its depender for the whole time it is invalid.** `beginsChanging` fires when it goes invalid; the matching `endsChanging` fires **only when it becomes valid again**. Proof: the un-inform/`dependencyEndsChanging` block in `__endTransaction` runs only when `inform` is true, gated on `__valid`; a value that closes its last transaction still invalid under `__shouldRemainInvalid` does **not** tell its dependers it ended (ARLD), so they keep the transaction open.

You may still **`set` a depender `D` while its dependency `X` is invalid** ("write despite invalid dependencies"). `D.set` (PileImpl) opens its own "setting" transaction and makes `D` valid. The interaction with `X` is mediated by a **separate mechanism layered on top of transactions: the deep-revalidate registry** (`thisNeedsDeepRevalidate` per depender, `dependersNeedingDeepRevalidate` per dependency — ARLD):

1. On that `set`, `D` computes `needDeepRevalidate = (invalidDependencies ∪ changingDependencies) ≠ ∅` (PileImpl) and calls `__thisNeedsDeepRevalidate(true)`, which **propagates up to `X`** via `__dependerNeedsDeepRevalidate` (ARLD). `X` now records "`D` is valid despite me being invalid."

2. **`X` becomes valid by recomputation** → `X`'s transaction on `D` ends; `D` runs `__endTransaction` while **valid** → `startRecomputation=false`, scheduled recompute cancelled (ARLD). **`D` keeps its manually-set value.** (README: "valid Dependers should not be invalidated.")

3. **`X` is instead set manually** → `set` calls `fireDeepRevalidateOnSet` *before* `X` turns valid (PileImpl) → `fireDeepRevalidate` (ARLD, only fires while `X` is invalid) → walks `dependersNeedingDeepRevalidate` → `D.deepRevalidate` = `revalidate+fireDeepRevalidate` (PileImpl), recursing through the chain. **All transitive dependers revalidate.** (README: "if an invalid Dependency is set manually, all transitive Dependers will be invalidated.")

**So:** transactions carry the synchronous "in flux, don't recompute, remember old value" propagation; the deep-revalidate registry is orthogonal bookkeeping that distinguishes *my invalid dependency finished recomputing* (keep my manual value) from *my invalid dependency was set manually* (cascade through me). They cooperate; they do not conflict.

## Caveats & gotchas

- The ARLD list says "a dependency is **invalid** [opens a transaction]". Precisely: it's open from the dependency going invalid until it becomes valid again — there is no separate "settled-invalid, transaction closed" state for a normal dependency (end fires on becoming valid).
- `transaction(false)` keeps the value valid during the batch — readers see the *pre-batch* value until release. `transaction(true)` makes it invalid during the batch (on `PileImpl`).
- Recomputation is gated by *both* the transaction counter  and `allDependenciesValid` ; a value with no open client transaction can still refuse to recompute because a dependency is invalid.
- The `_transactionReasons` tracking and `creationTrace` only exist when `DebugEnabled.DE` is true; don't rely on them in production builds.
- Brackets (`open`/`close`) fire from inside transaction/validity transitions while the `mutex` is held — see the brackets doc (TODO) for what you must not do there.
- A transaction batches the reactions of **dependers** (they recompute once, at commit), **not** the transacting value's own direct `ValueListener`s. To observe coalescing, watch a *downstream* value, not the one you're writing. (Confirmed by the transaction check in `tests/pile/tests/PileCoreTests`.)

## To verify with a characterization test (before treating as guaranteed)

When `X` recomputes to a **genuinely different** value while `D` was manually `set` valid: the code paths (`changed` flag in `dependencyEndsChanging` vs. the valid-branch cancel in `__endTransaction`) indicate **`D` keeps its manual value**, but this is exactly the kind of ordering subtlety that deserves a golden test in `tests/` rather than reading alone. Until that test exists, treat step (2) above as "strongly indicated" rather than "contractually guaranteed".

## Related

- `Recomputation` / `Recomputer` (recomputation lifecycle, dynamic dependencies) — doc TODO.
- `Suppressor` (`pile.aspect.suppress`) — the release-to-end handle used by `transaction`.
- Validity, `Dependency`/`Depender` — see [../overview.md](../overview.md).
