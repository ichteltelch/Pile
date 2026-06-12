# `ReferenceCounted`

Reference-counting lifecycle aspect: objects count references to themselves and may self-destruct when the count hits zero.

Source folder: `src` — `pile.aspect.ReferenceCounted`.

Up: [package index](_index.md) · [overview](../../overview.md). Related: [`suppress/Suppressor`](suppress/Suppressor.md) *(doc pending)*, [`bracket/ValueBracket`](bracket/ValueBracket.md) *(doc pending)*.

## What it's for

A tiny aspect interface (4 members, no state) for objects whose lifetime is governed by an explicit, caller-managed reference count rather than by GC. Increment when you start using the object, decrement when you're done; reaching zero **may** trigger destruction of the object. It is *not* implemented by any value type inside the Pile library itself — Pile only *consumes* it (via `ValueBracket`, below). The implementors live in downstream application code (e.g. mesh/model resources in Biss).

## Members by purpose

- `increaseRefcount` / `decreaseRefcount` — the two count operations. The javadoc on `decreaseRefcount` is the load-bearing contract: *"If it reaches zero, this **may** trigger destruction of the object"*. "May" is deliberate — whether zero means destroy-now, destroy-later, or recycle is left entirely to the implementor; this interface neither defines a destroy hook nor guarantees one runs.
- `rcReferenceKeeper` *(default)* — the **handle idiom**: increments the count immediately and returns a [`Suppressor`](suppress/Suppressor.md) whose `release` calls `decreaseRefcount` once. This packages a balanced inc/dec pair into a single releasable token, so callers can hold a reference with try-with-resources / `finally` instead of hand-matching inc/dec calls.
- `KEEP_REFERENCE` *(static `Function<ReferenceCounted, Suppressor>`)* — just a method handle for `rcReferenceKeeper`, for passing the keeper-creation as a first-class function.

## How it ties into Pile: the bracket integration

Pile's only internal use is in [`ValueBracket`](bracket/ValueBracket.md), which offers ready-made brackets that ref-count a value (or each element of a collection) for the duration that the value is held by a reactive value-holder:

- `REF_COUNT_BRACKET` — opens by `increaseRefcount`, closes by `decreaseRefcount`; `nopOnNull` so a null value is skipped.
- `COLLECTION_REF_COUNT_BRACKET` — same, applied per non-null element of an `Iterable`. **Caveat (from the javadoc):** the collection must not be mutated while bracketed, or counters won't balance — they may never reach zero, or reach zero prematurely.
- `queuedRefCountBracket(SequentialQueue)` / `queuedCollectionRefCountBracket(SequentialQueue)` — variants whose *close* (decrement) runs on a [`SequentialQueue`](../../pile/utils/), while the *open* (increment) still runs synchronously via `beforeOpening`. Use these when destruction-at-zero must happen on a specific thread/queue.

The asymmetry in the queued variants is intentional: increment eagerly (synchronously) so the reference is held before anything else can run, but defer decrement (the side that may destroy) onto the queue.

## Salient behavior & gotchas

- **Zero ≠ guaranteed destruction.** The interface only says destruction *may* be triggered. Don't assume `decreaseRefcount` reaching zero frees resources synchronously, or at all — that's the implementor's decision.
- **Balance is the caller's responsibility.** Nothing here detects leaks or over-releases. The `rcReferenceKeeper` / `Suppressor` idiom exists precisely to make balancing automatic; prefer it over raw inc/dec pairs.
- **`Suppressor` here is a generic releasable token**, not Pile's transaction/suppression machinery — `rcReferenceKeeper` just wraps `decreaseRefcount` as the release action (`Suppressor.wrap`).
- **No in-library implementors.** Searching the library finds usages only in `ValueBracket`; concrete `ReferenceCounted` types are external. The override map that other aspect docs carry does not apply.

## Common tasks

- **Hold a reference safely:** `try (Suppressor s = obj.rcReferenceKeeper) { … }` (or keep `s` in a field and `s.release` when done) — auto-balanced.
- **Tie ref-count to a reactive value's held value:** install `ValueBracket.REF_COUNT_BRACKET` (or the collection variant) on the holder so the count tracks how long the value is retained.
- **Run destruction off-thread:** use `queuedRefCountBracket(queue)` so the decrement (and any destruction it triggers) is dispatched on `queue`.

## Tech debt / warts

- Minimal contract: no destroy callback, no count accessor, no leak detection — everything beyond inc/dec is by convention.
- The "may trigger destruction" wording leaves destruction semantics undocumented at this layer; behavior is only knowable per implementor.
