# `pile.impl.TransactionTracker`

Source folder: `src`. File: `pile/impl/TransactionTracker.java` (~51 lines).

A **debug-only record documenting *why* a transaction is open** on a reactive value. Public "in case alternative implementations find it useful," but in practice only populated when `DebugEnabled.DE` is set — see [concepts/transactions.md](../../concepts/transactions.md) for how transactions work.

## What it is
An immutable tuple identifying one open-transaction reason:
- `originator` — the object responsible (the value itself, or a changing `Dependency`).
- `reason` — a `String` tag, e.g. `"pending recomputation"`, `"ongoing recomputation"`, `"dependency changing"`, `"setting"`, `"revalidating"`, `"Manual"`.
- `id` — an optional disambiguator (so two otherwise-identical reasons can coexist).
- `trace` — a creation stack trace, captured **only if `DebugEnabled.TRANSACTION_TRACES`**; otherwise `null`.

## How it's used
`PileImpl` keeps a `Set<TransactionTracker> _transactionReasons` (allocated only under `DebugEnabled.DE`) and adds/removes a tracker each time it opens/closes a transaction, in lockstep with the `openTransactions` counter. In a debugger you can inspect that set to see exactly which reasons are keeping a value in transaction (and thus why it won't recompute). See [`PileImpl`](PileImpl.md) and [transactions.md](../../concepts/transactions.md) § one counter, several reasons.

## Salient behavior
- **Equality**: equal iff same `originator` (identity), equal `reason` (`Objects.equals`), and same `id` (identity). `hashCode` uses `originator` + `reason` (not `id`).
- `toString` is `reason + ": " + originator` — what you read in the debugger.

## Caveats
- **Debug-only.** With `DebugEnabled.DE` false this class is never instantiated by the framework (the `_transactionReasons` set isn't even allocated). Don't rely on it in production logic.
- `trace` is `null` unless `DebugEnabled.TRANSACTION_TRACES` — enabling it is expensive.

## Related
- [`PileImpl`](PileImpl.md) · [`DebugCallback`](DebugCallback.md) · [concepts/transactions.md](../../concepts/transactions.md).
