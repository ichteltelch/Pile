# `pile.aspect.DoesTransactions`

Source folder: `src`. File: `pile/aspect/DoesTransactions.java`.

The **client-facing aspect interface a reactive value implements when it can be in a "transaction ongoing" state**. A transaction is an *"this value is in flux"* state: while one is open the value does not recompute, and the value held at the start is remembered so a real change can be detected when it closes.

This page is the concise API reference for the interface. The **model, the internal counter machinery, old-value remembering, the recompute gate, and the deep-revalidate interaction live in [concepts/transactions.md](../../concepts/transactions.md)** — read that for *how transactions actually work*; read this for *what `DoesTransactions` exposes*. See the [overview](../../overview.md) for where this sits in the architecture.

## What it is for

It gives callers a way to **batch several manual writes that the framework cannot know belong together** (e.g. the `x`, `y`, `z` of a point) so dependers recompute *once*, against a consistent snapshot, rather than on each intermediate write. Concrete piles get the interface through the assembled contracts (`Pile` extends it) and the `AbstractReadListenDependency` (ARLD) base, which supplies the actual implementation of the abstract methods declared here.

The interface itself is tiny: two `default` convenience methods (`transaction` / `transaction(boolean)`), two abstract low-level hooks (`__beginTransaction` / `__endTransaction`), and two reified method-handle constants. Everything behavioral is delegated to the implementor's `__beginTransaction` / `__endTransaction`.

## Methods

### `transaction(boolean invalidate)` — open a transaction *(default)*
Opens a transaction and returns a [`Suppressor`](suppress/Suppressor.md); **releasing the `Suppressor` ends the transaction**. Implementation is literally `Suppressor.wrap(this::__endTransaction)` followed by `__beginTransaction(invalidate)` — i.e. it wires the end hook to the suppressor, then begins.

The `invalidate` flag: *if the value supports invalidity and no transaction was already open*, invalidate the value for the duration of the transaction (or until it is set explicitly).

### `transaction` — open with invalidation *(default)*
Calls `transaction(true)`.

### `__beginTransaction` / `__beginTransaction(boolean invalidate)`
The low-level entry. `__beginTransaction` defaults to `__beginTransaction(true)`; `__beginTransaction(boolean)` is **abstract** and implemented by ARLD. Use these "for more comfort but less efficiency, consider calling `transaction` instead" — the Javadoc's words inverted; the point is the `transaction` form is the comfortable one and these are the efficient-but-manual one. **You must pair every `__beginTransaction` with a matching `__endTransaction` yourself**; prefer the `Suppressor` form, which pairs them for you.

### `__endTransaction`
Abstract; ends one transaction. Its Javadoc says it "Calls `__endTransaction(boolean)`" — that overload is **not** declared on this interface; it is the ARLD method (`AbstractReadListenDependency.__endTransaction(boolean)`, ARLD per the concept doc) that the implementation forwards to.

### Constants (reified method handles)
- `TRANSACTION_METHOD : Function<DoesTransactions, Suppressor>` — `DoesTransactions::transaction`, i.e. `transaction(true)`.
- `TRANSACTION_METHOD_NO_INVALIDATE : Function<DoesTransactions, Suppressor>` — `v -> v.transaction(false)`.

These exist so a transaction-opener can be passed where a `Function<DoesTransactions, Suppressor>` is expected (the same reification pattern as `Dependency.SUPPRESS_DEEP_REVALIDATION`).

## The `invalidate` semantics (and the `PileImpl` vs `Independent` difference)

`invalidate` only has an effect *if* the value supports invalidity *and* no transaction was already open:

- **`PileImpl` makes the value invalid** for the transaction's duration.
- **`Independent` keeps it valid.**

(`DoesTransactions.java`.) Consequently `transaction(false)` keeps the value valid during the batch — readers see the *pre-batch* value until release — whereas `transaction(true)` (the `transaction` default) makes a `PileImpl` invalid during the batch. This per-implementation split is the single most important caveat of the interface.

## Salient behavior

- **Suppressed recomputation:** as long as transactions are active that are *not* due to pending recomputations, the value must not recompute itself. The actual gate lives in `PileImpl.__startPendingRecompute` — see [transactions § the recompute gate](../../concepts/transactions.md).
- **Old-value remembering & change detection:** at the start of the (first) transaction the current value is copied to an "old value" field unless that field is already occupied; when *all* transactions have ended, the now-current value is compared via `_getEquivalence` to the stored old value to decide whether a change occurred. The mechanics (`moveValueToOldValue`/`copyValueToOldValue`, restore-on-no-change) are in [transactions § old-value remembering](../../concepts/transactions.md).
- **Nesting via a counter:** transactions are reference-counted, not boolean — many can be open at once (opened both internally and by clients); only when the count returns to zero does the value settle and compare. See [transactions § one counter, several reasons](../../concepts/transactions.md).

## Caveats & gotchas

- **Pair your `__begin`/`__end` calls.** The `__`-prefixed methods are private-by-convention; misuse leaves the value stuck in transaction (counter never returns to zero, so it never recomputes). Use `transaction` unless you have a measured reason not to.
- **`invalidate` is conditional.** It is ignored if a transaction is already open, or if the value type has no invalid state (`Independent`). Don't assume `transaction(true)` always invalidates.
- **The Javadoc is slightly off:** the "`Calls __endTransaction(boolean)`" note references a method that is not on this interface, and the `@return` tags on `transaction`/`transaction(boolean)` are empty — consistent with the project-wide note that some API is unsystematic (see [overview § caveats](../../overview.md)).

## Common tasks (how to…)

- **Batch several manual writes:**
  ```java
  Suppressor t = value.transaction;      // transaction(true)
  try { value.set(x); value.set(y); … }    // dependers don't react yet
  finally { t.release; }                 // closing the Suppressor ends the transaction
  ```
- **Batch without invalidating** (readers keep seeing the pre-batch value): use `value.transaction(false)`.
- **Pass a transaction-opener as a function:** use `TRANSACTION_METHOD` or `TRANSACTION_METHOD_NO_INVALIDATE`.
- **Open/close manually for efficiency:** `value.__beginTransaction(invalidate); try { … } finally { value.__endTransaction; }` — but the `Suppressor` form is preferred.
- **Observe transaction state reactively:** not on this interface — use `Dependency.isInTransaction` / `inTransactionValue` (see [`Dependency`](Dependency.md) and ARLD).

## Tech debt / warts

- The `__begin`/`__end` "do not forget to pair" contract is enforced only by convention; the `Suppressor` form exists precisely to paper over this.
- Javadoc inaccuracies noted above (, empty `@return`s).
- The interface declares no way to *read* transaction state; that lives on `Dependency`/ARLD, so the capability is split across interfaces.

## Related

- [concepts/transactions.md](../../concepts/transactions.md) — the full model, internals, recompute gate, old-value remembering, and the deep-revalidate interaction. **Start here for behavior.**
- [`Suppressor`](suppress/Suppressor.md) — the release-to-end handle returned by `transaction`.
- [`Dependency`](Dependency.md) — sibling aspect; exposes the read-only `isInTransaction` / `inTransactionValue` view.
- [overview.md](../../overview.md) — architecture map.
