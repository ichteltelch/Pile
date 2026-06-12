# `pile.aspect.bracket.QueuedValueBracket`

A `ValueBracket` decorator that runs the wrapped bracket's `open`/`close` on a [`SequentialQueue`](../../utils/) — off the owner's mutex, in enqueue order.

Source folder: `src`. File: `pile/aspect/bracket/QueuedValueBracket.java`.

`QueuedValueBracket<E, O>` wraps a backing [`ValueBracket`](ValueBracket.md) (`back`) and, instead of invoking `back.open`/`back.close` synchronously, **enqueues** them onto a `SequentialQueue`. This is the primary tool for the [`ValueBracket`](ValueBracket.md) "mutex-held caveat": the framework calls `open`/`close` while the owner's `mutex` is held, so any reentrant or destructive work must be deferred. The decorator's own `open`/`close` return **synchronously** (computed by the `keep`/`remain` predicates), while the real effect happens later on the queue. Reach it via the `queued(...)` decorator family on [`ValueBracket`](ValueBracket.md) rather than constructing it directly.

See also the package [_index.md](_index.md), the [overview](../../../overview.md), and [concepts](../../../concepts/).

## The queue it uses

The queue is a [`SequentialQueue`](../../utils/) (undocumented as of writing — forward-link). It is a single-consumer FIFO that runs each enqueued `Runnable` in submission order on its own worker thread. Two ways to supply one:

- **Explicit** — pass a `SequentialQueue` to the constructor (or to `queued(q, …)`). Reuse one queue across many brackets; a fresh queue per bracket can mean a thread per bracket.
- **Default** — the no-arg `queued` overloads use `QueuedValueBracket.getDefaultQueue`, a lazily-created process-wide queue named `"Default ValueBracket Queue"`. Replace it with `setDefaultQueue(...)`.

## Ordering guarantees — is open-before-close preserved?

**Yes, for a given queue.** A `SequentialQueue` is FIFO, so if the framework calls this bracket's `open` and later its `close`, the `back.open` task is enqueued before the `back.close` task and therefore runs first. The *synchronous* return of `open`/`close` may happen well before the corresponding queued task executes, but the **relative order of the deferred effects matches the order of the calls** — provided both go through the same queue. (Using different queues for open and close, which the API does not let you do for one bracket, would break this.)

Caveat: because the effect is deferred, by the time `back.open` actually runs the owner may already have moved on (e.g. been invalidated/closed). The queued task captures `value` and `owner` at enqueue time, so it operates on the snapshot, not the current state.

## Obsolescence handling (the `obsoleteOn` map)

A wrinkle absent from the simpler brackets: `QueuedValueBracket` supports the wrapped bracket *becoming obsolete* asynchronously. An `obsoleteOn` `ConcurrentHashMap<O,Object>` is allocated iff `keep!=null || back.canBecomeObsolete`.

- On `open`: if `owner` is already recorded obsolete, it is removed and `open` returns `false` immediately (the bracket is discarded — see [ValueBracket.md](ValueBracket.md) "open ⇒ keep-the-bracket"). Otherwise the queued task runs `back.open`, and **if that returns `false`** (the backing bracket became obsolete) the owner is recorded into `obsoleteOn` so the *next* `open` for that owner can short-circuit.
- `canBecomeObsolete` returns `true` iff that map exists.

This is how an asynchronously-deferred bracket propagates the backing bracket's obsolescence back into the synchronous protocol, despite `back.open`'s result not being available until later.

## Return values & metadata

The synchronous booleans follow the [`ValueBracket`](ValueBracket.md) contract and are computed from the constructor predicates (all may be `null`):

- `open` returns `remain==null || remain.test(value, owner)` — i.e. **`true`** (keep the bracket) unless a `remain` predicate says otherwise. (Note: it is the `remain` predicate — not `keep` — that drives `open`'s return.)
- `close` returns `keep!=null && keep.test(value, owner)` — i.e. **`false`** (don't keep the value reference) unless a `keep` predicate says otherwise.
- `filter` (if non-null) gates whether anything is enqueued at all — typically used to skip `null` values (`ValueBracket.NON_NULL`); the bare `queued(q)` decorator supplies a null-filter.
- `openIsNop`/`closeIsNop` are `true` only when both the predicate side and the corresponding backing side are no-ops, so an all-nop backing bracket stays cheap.
- `isInheritable` delegates to `back`.

`backDoesOpen`/`backDoesClose` are cached at construction from `back.openIsNop`/`back.closeIsNop`; if the relevant side is a backing no-op and there's no predicate, nothing is enqueued.

## How it differs from `DeferredValueBracket`

[`DeferredValueBracket`](DeferredValueBracket.md) is a **near-identical twin**: same fields, same `open`/`close`/obsolescence logic, same `filtersFirst` reordering, same `ValueOnly` nested subclass. The sole difference is the deferral target:

| | runs effect via | construct/decorator |
|---|---|---|
| `QueuedValueBracket` | a `SequentialQueue` (`queue.enqueue(...)`) | `queued(...)` |
| `DeferredValueBracket` | a `Deferrer` (`deferrer.run(...)`) | `defer(Deferrer)` |

Use `QueuedValueBracket` when you want strict FIFO ordering on a dedicated worker thread; use `DeferredValueBracket` when the host supplies a `Deferrer` policy (e.g. coalescing or EDT-style scheduling) whose ordering/threading is the `Deferrer`'s business.

## The `queued` factory

There is no public `queued` *factory* on this class itself — the entry points are the `queued(...)` **decorator methods on [`ValueBracket`](ValueBracket.md)** (`bracket.queued(q, filter, keep, remain)`, `queued(q, filter)`, `queued(q)`, the `queued(name, …)` forms that allocate a new queue, and the no-arg `queued` using `getDefaultQueue`). The bare `queued(q)` form installs a null-value filter. See [ValueBracket.md](ValueBracket.md) "Decorator catalogue → queued".

`filtersFirst` is the only non-trivial decorator-aware method: it pushes a wrapped [`FilteredBracket`](FilteredBracket.md) *outside* the queue wrapper so filtering happens synchronously (before enqueuing) rather than on the queue. The nested `ValueOnly` subclass is the [`ValueOnlyBracket`](ValueOnlyBracket.md) (owner-agnostic) specialization with its own `filtersFirst`.

## Caveats & gotchas

- **The effect is asynchronous** — `open`/`close` return before `back.open`/`back.close` run. Don't rely on the effect having happened when the bracket call returns. State captured in the queued task is the snapshot at enqueue time.
- **`open`'s return is governed by `remain`, `close`'s by `keep`** — the cross-naming is easy to misread; it matches [`DeferredValueBracket`](DeferredValueBracket.md) and the constructor javadoc.
- **One queue, one thread** — reuse a `SequentialQueue` across brackets; the `queued(name)` forms each spin up a new one.
- **Same-queue ordering only** — open-before-close ordering holds because both effects share this bracket's single queue.

## Tech debt / warts

- **Commented-out "dependency queue" machinery** — a dead `defaultDependencyQueue` getter/setter pair left in source.
- **Broken double-checked locking in `getDefaultQueue`** — see Suspected bugs; the lazy-init guard never actually checks the field inside the synchronized block.
- **Near-total duplication with [`DeferredValueBracket`](DeferredValueBracket.md)** — the two classes differ by one field and one call site; a shared base could collapse them.
