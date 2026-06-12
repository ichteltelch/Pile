# `WriteElsewherePile`

Vestigial, **entirely commented-out** combination interface that would have married [`Pile`](Pile.md) to the (equally dead) [`WriteElsewhere`](../WriteElsewhere.md) deferred-write marker; currently inert (no symbol on the classpath).

Source folder: `src` — `pile/aspect/combinations/WriteElsewherePile.java`.

Up: [combinations index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md). Recompute/transaction model: [concepts/transactions.md](../../../concepts/transactions.md).

## Status: inert / dead code

The whole file — including the `package` declaration and every import — is `//`-commented. Consequences:

- There is **no `pile.aspect.combinations.WriteElsewherePile` interface** and **no `WriteElsewherePileImpl` class** on the classpath. Nothing can implement, extend, import, or reference them.
- Confirmed by the language server: searching the indexed source for `WriteElsewherePile`, `WriteElsewherePileImpl`, and the method `wouldDefer` all return **zero symbols** and zero references. It is wired into nothing.
- Its would-be supertype [`WriteElsewhere`](../WriteElsewhere.md) is itself fully commented out, so even reviving this file would require reviving that one first.

Document it as **abandoned scaffolding**, not a working feature.

## What it was *intended* to be

From the commented-out sketch, the design was a **write-deferring wrapper** around an existing read/write dependency:

> `interface WriteElsewherePile<E> extends Pile<E>, WriteElsewhere` — a `Pile` that may run its writes on **another thread** or **later**, governed by an `ExecutorService` (`deferTo`) and a `BooleanSupplier` (`wouldDefer`).

A factory `make(wrapped, deferTo, wouldDefer)` would have produced the nested `WriteElsewherePileImpl<T> extends PileImpl<T>`. The impl's shape:

- **Construction**: builds itself via `PileBuilder`, recomputing from `wrapped.get` and revalidating `whenChanged(wrapped)` — i.e. it mirrors a wrapped value but interposes on writes.
- **`wouldDefer`**: delegates to the supplied `BooleanSupplier` — the live realization of the `WriteElsewhere` marker method.
- **`set(val)`**: the heart. If `wouldDefer` and not in a transaction, it applies the value optimistically via `super.set`, then submits the *real* `wrapped.set(...)` to the executor and writes its result back asynchronously. Inside a transaction it instead registers a listener on `wrapped.inTransactionValue` and re-issues the `set` once the transaction closes. The non-deferred path writes through to `wrapped` synchronously and copies the result back. The whole body runs under `ListenValue.DEFER` suppression.
- **`applyCorrection`** chains `super` then `wrapped`'s correction; **`informLongTermInvalid`** is itself dispatched to the executor when deferring.

So the concept: a `Pile` facade over another value that absorbs writes immediately for read-back but flushes them to the underlying value off-thread / post-transaction, advertising this via `wouldDefer`.

## Relationship to live API

- [`Pile`](Pile.md) — the live capstone this would have extended; the real recompute/transaction/seal contract lives in `PileImpl`. `Pile` has **no** defer hook in the shipped API.
- [`WriteElsewhere`](../WriteElsewhere.md) — the (also dead) one-method marker (`wouldDefer`) this combination would have implemented concretely.
- Asynchronous/delayed recomputation that *did* ship lives in the **builders** (`pile.builder`, "threaded/delayed recomputation" — [overview](../../../overview.md)) and in executors under `pile.interop.exec`. Those cover the *recompute* side; this file's distinctive idea — deferring the **write** path while keeping a synchronous optimistic read-back — was never activated.
- Redirecting *where* a write goes is handled live by `Sealable` (seal-redirect mode); that is orthogonal to the *when/which-thread* deferral contemplated here.

## Caveats & gotchas

- **Do not cite this as an existing capability.** There is no framework "defer this write" facility today; using this design means reviving both this file and `WriteElsewhere`.
- The sketch contains debugging cruft — `if(v==null) System.out.print("")` no-op breakpoint anchors — and a commented-out `pendingSet`/`doOnceWhenValid` alternative. It is clearly unfinished, not merely disabled.
- The transaction-path re-`set` and the optimistic `super.set` before the async `wrapped.set` encode subtle ordering assumptions that were never validated against the live transaction model ([concepts/transactions.md](../../../concepts/transactions.md)); treat the sketch as intent, not as a verified algorithm.

## Tech debt / warts

- A commented-out source file pollutes the package: it appears in directory listings as if it were a real unit, yet is invisible to tooling (no symbol, no Javadoc). Either implement it (after reviving `WriteElsewhere`) or delete it. The combinations index already flags it as the (vestigial) write-redirection relation ([`_index.md`](_index.md)).
