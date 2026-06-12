# `pile.aspect.bracket.DeferredValueBracket`

A [`ValueBracket`](ValueBracket.md) decorator that runs the wrapped bracket's `open`/`close` *effect* on a [`Deferrer`](../../utils/) instead of synchronously, while computing the return booleans on the spot — so the effect leaves the owner's `mutex` but the framework still gets its keep/remain answers immediately.

Source folder: `src`. File: `pile/aspect/bracket/DeferredValueBracket.java`.

Created via the `defer(Deferrer)` decorator on [`ValueBracket`](ValueBracket.md) (see its decorator catalogue). The near-identical twin that defers to a `SequentialQueue` is [`QueuedValueBracket`](QueuedValueBracket.md). See the package [_index.md](_index.md), the [overview](../../../overview.md), and the mutex-held caveat in [ValueBracket.md](ValueBracket.md).

## What it does

Wraps a backing bracket `back`. On `open`/`close` it splits the work in two:

- **Effect** (the actual `back.open`/`back.close` call) — wrapped in a `Runnable` and handed to `deferrer.run(...)` rather than invoked inline. Where/when that runs is the `Deferrer`'s business.
- **Return value** — decided *synchronously* by the `keep`/`remain` `BiPredicate`s, so `ARLD` gets its answer without waiting for the deferred effect.

This is the idiomatic fix for the [ValueBracket](ValueBracket.md) mutex-held caveat: heavy/reentrant/destructive open/close work must not run under the owner's `mutex`.

## Constructor parameters

`DeferredValueBracket(back, deferrer, filter, keep, remain)`:

- **`back`** — the wrapped bracket whose effect is deferred.
- **`deferrer`** — the [`Deferrer`](../../utils/) the effect is dispatched to.
- **`filter`** — gates whether the effect is scheduled at all; if `null`, never filters. Typical use is dropping `null` values (cf. `NON_NULL`).
- **`keep`** — computes `close`'s return (keep-the-value-reference). `null` ⇒ `close` returns `false`.
- **`remain`** — computes `open`'s return (keep-the-bracket). `null` ⇒ `open` returns `true`.

Note the naming: `keep` drives `close`, `remain` drives `open` — the asymmetry described in [ValueBracket.md](ValueBracket.md) (§ the open/close contract).

## What is deferred, and to where

Only the **effect** is deferred, and only to the supplied `Deferrer`. The `Deferrer` interface (`pile/utils/defer/Deferrer.java`) is just `void run(Runnable)` plus suppress/state queries; concrete impls decide the timing (e.g. batch-and-flush, thread-local). The sentinel `Deferrer.DONT` runs the `Runnable` inline — passing it makes a `DeferredValueBracket` behave synchronously.

## Ordering guarantees

**None beyond what the `Deferrer` provides.** Unlike [`QueuedValueBracket`](QueuedValueBracket.md), which enqueues onto a single `SequentialQueue` (FIFO, serialized) so a value's `open` is guaranteed to run before its later `close`, `DeferredValueBracket` only calls `deferrer.run(...)`. Ordering of deferred opens/closes is whatever the chosen `Deferrer` implementation guarantees. Choose the `Deferrer` accordingly if you rely on open-before-close.

## Difference from `QueuedValueBracket`

The two classes are byte-for-byte twins except for the dispatch line:

- `DeferredValueBracket` → `deferrer.run(runnable)`
- [`QueuedValueBracket`](QueuedValueBracket.md) → `queue.enqueue(runnable)`

Everything else — `filter`/`keep`/`remain` semantics, the `obsoleteOn` map, `canBecomeObsolete`, `openIsNop`/`closeIsNop`, the `ValueOnly` subclass, `filtersFirst` — is identical. Pick `Deferred` for a pluggable/thread-local deferral policy (or to collapse to synchronous via `Deferrer.DONT`); pick `Queued` for a dedicated serialized off-mutex queue.

## Obsolescence handling (`obsoleteOn`)

Because the real `open` runs later, its keep-the-bracket result can't be returned inline. The bracket carries a `ConcurrentHashMap<O,Object> obsoleteOn` (allocated only if `keep!=null || back.canBecomeObsolete`, ):

- When the deferred `back.open` returns `false` (bracket became obsolete), the owner is recorded in `obsoleteOn`.
- The next `open(value, owner)` checks-and-removes that entry first and returns `false` immediately, propagating the deferred obsolescence to ARLD.

`canBecomeObsolete` is `true` iff `obsoleteOn` was allocated.

## Nop / metadata semantics

- `openIsNop` ⇒ `keep==null & !backDoesOpen` — i.e. nothing to defer on open *and* nothing to return. Note this guards on `keep`, but `open`'s return is actually driven by `remain`; see warts.
- `closeIsNop` ⇒ `remain==null & !backDoesClose` — symmetrically guards on `remain`, but `close`'s return is driven by `keep`; see warts.
- `backDoesOpen`/`backDoesClose` are cached at construction from `!back.openIsNop` / `!back.closeIsNop`, so a bracket whose backing side is a no-op never schedules anything.
- `isInheritable` delegates straight to `back`.

## `filtersFirst`

Mirror of the `QueuedValueBracket` logic: if the backing bracket reorders to put a `FilteredBracket` outermost, this rebuilds so the *filter* wraps the deferral (, and the `ValueOnly` override ) — keeping the cheap synchronous filter ahead of the deferred effect. Returns `this` when nothing reorders.

## Caveats & gotchas

- **No intrinsic ordering** — see Ordering guarantees. If you need open-before-close serialization, use [`QueuedValueBracket`](QueuedValueBracket.md) or a serializing `Deferrer`.
- **Effect runs later, off the mutex** — by design, but it means the value/owner may already have moved on by the time `back.open`/`back.close` executes. The synchronous `keep`/`remain` answers are what ARLD sees.
- **`keep` drives `close`, `remain` drives `open`** — the cross-wiring is easy to misread.
- **`Deferrer.DONT` undoes the deferral** — handy, but a silent way to make a "deferred" bracket synchronous again.

## Tech debt / warts

- **`openIsNop`/`closeIsNop` guard on the wrong predicate.** `openIsNop` tests `keep` though `open`'s return uses `remain` (and vice-versa for `closeIsNop`/`keep`). The same crossed guards exist verbatim in [`QueuedValueBracket`](QueuedValueBracket.md), so it is a shared, deliberate-looking pattern rather than a local typo — but the metadata can disagree with the actual return path when only one of `keep`/`remain` is non-null. Flagged for the developer's judgement.
- **Copy-paste twin of `QueuedValueBracket`** — the two classes differ only in one dispatch call; any fix to obsolescence/nop logic must be made in both.
