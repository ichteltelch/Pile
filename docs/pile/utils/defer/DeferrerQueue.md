# `DeferrerQueue`

The pluggable store of pending `Runnable`s backing a deferrer — an interface with three nested ordering strategies (`FiFo`, `LiFo`, `Dedup`).

Source folder: `src`. Package `pile.utils.defer`.

Up: [defer index](_index.md) · [overview](../../../overview.md). Interface contract: [Deferrer.md](Deferrer.md). Users: [DefererImpl.md](DefererImpl.md), [ThreadLocalDeferrer.md](ThreadLocalDeferrer.md).

## What it's for

A `DeferrerQueue` holds work that a deferrer has collected to run later (at a flush point). It abstracts *which order* the deferred `Runnable`s come back out and *whether* duplicates are collapsed. The deferrer enqueues with `enqueue`, and at flush time drains by repeatedly calling `pollQueue` until `isQueueEmpty` (or `pollQueue` returns `null`).

## Contract (3 methods)

- `enqueue(Runnable r)` — add work.
- `pollQueue()` — remove and return the next item to run, or `null` if empty.
- `isQueueEmpty()` — whether nothing is pending.

Note `pollQueue` returning `null` is the same empty signal as `isQueueEmpty()` being true, so a drain loop can test either.

## The three strategies

### `FiFo`
Backed by an `ArrayDeque`. `enqueue` appends (`addLast`), `pollQueue` takes from the front (`pollFirst`). **First-in-first-out** — deferred work runs in the order it was registered. No dedup.

### `LiFo`
Backed by an `ArrayList`. `enqueue` appends, `pollQueue` removes the **last** element. **Last-in-first-out (stack)** — most recently deferred work runs first. No dedup.

### `Dedup`
A keyed queue that collapses repeated enqueues of the **same `Runnable`** (equality/`hashCode` via a `HashMap<Runnable, Entry>`) into one pending entry. Order is maintained by a separate **intrusive circular doubly-linked list** of `Entry` nodes anchored by a `sentinel`; the map only locates a node for O(1) dedup. `pollQueue` returns `sentinel.next` (the oldest live entry) — so among the surviving entries the drain is **FIFO**.

Re-enqueue policy is controlled by `stayDontMoveToEnd` (a constructor flag), optionally overridden **per `Runnable`** when the runnable implements the nested `Dedup.StayMoveToEnd` interface (`stayDontMoveToEnd()` is consulted in `Dedup.enqueue` and takes precedence over the constructor default):
- **stay = false** (move-to-end): re-enqueuing an already-pending runnable removes it and re-inserts it before the sentinel, so it becomes the *newest* — its position resets to the back.
- **stay = true** (don't move): a re-enqueue of a pending runnable is a no-op; the entry keeps its original position.

Dedup keys on the `Runnable` instance/equality, so to actually deduplicate you must enqueue the *same* (or `equals`-equal) runnable object — fresh lambdas capturing different state will not collapse.

## Enqueue during a drain

None of the three strategies snapshot. They poll one item at a time from the live structure, so a `Runnable` that enqueues more work **during the drain** simply adds to the same queue and that new work will be polled in the same drain pass (subject to the strategy's order — appended at the back for `FiFo`, taken next for `LiFo`). This unit does not itself loop; the drain loop lives in the deferrer (see [DefererImpl.md](DefererImpl.md)).

## Caveats & gotchas

- **No thread safety.** `ArrayDeque`, `ArrayList`, `HashMap`, and the linked list are all unsynchronized. Concurrent enqueue/poll is the caller's responsibility (deferrers are typically thread-confined — see [ThreadLocalDeferrer.md](ThreadLocalDeferrer.md)).
- **`Dedup` identity sensitivity.** Dedup is by `Runnable` equality. Distinct-but-logically-equal lambdas won't merge unless they `equals` each other (Java lambdas don't), and stateful runnables used as map keys must have stable `hashCode`/`equals` for the lifetime they're pending.
- **`Dedup` re-enqueue semantics are easy to misread:** the *default* (constructor) policy can be flipped on a per-runnable basis via `StayMoveToEnd`, so two runnables in the same `Dedup` can behave differently on re-enqueue.

## Tech debt / warts

- The three implementations are nested inside the interface rather than top-level classes; there is no factory and no shared base, so callers `new` the variant directly.
- `Dedup.Entry.remove()` assumes the entry is linked (dereferences `prev`/`next`); calling it on an already-removed/unlinked entry would NPE. Within this class it is only ever called on live entries, so it is safe as used.
- `FiFo` uses `ArrayDeque` but `LiFo` uses `ArrayList` for its stack — a minor inconsistency (`ArrayDeque` could serve both).
