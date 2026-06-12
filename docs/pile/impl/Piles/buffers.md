# Buffers (the `Piles.buffer*` / `setup*Buffer` family) — detail doc

A **buffer** is a reactive value that *follows* a leader's value and validity, but is **not a dependency** of it. It tracks the leader through a plain `ValueListener`, not a dependency edge. That one design choice is the whole point and explains every property below.

Defined as `setup*` default methods on [`ISealPileBuilder`](../../builder/ISealPileBuilder.md) (source folder `src`); surfaced through the `Piles.buffer`/`writableBuffer`/`weakBuffer`/`rateLimited` factories (see the [Piles index](_index.md) "Buffers" and "Rate limiting" sections). Up: [overview](../../../overview.md). Related: [transactions](../../../concepts/transactions.md), [`AbstractReadListenDependency`](../AbstractReadListenDependency.md).

## Why buffers exist: shortening transaction cascades

The author's stated use case (in the javadoc of every variant): *"to shorten transaction cascades, as the follower value will not enter a transaction if the leader does."*

A normal derived value is a **depender** of its inputs, so when an input opens a transaction the whole reactive subtree downstream is dragged into it and only settles when the transaction commits. A buffer breaks that chain: because it follows by listener rather than by dependency, **the leader's transaction does not propagate into the buffer**. Dependers of the *buffer* therefore see updates decoupled from — and after — the leader's transaction, cutting the cascade short. The buffer is the seam you insert when you want a value to mirror another without inheriting its transactional churn.

## The shared mechanism

Every variant (`setupBuffer`, `setupWritableBuffer`, `setupWeakBuffer`, `setupWritableWeakBuffer`, `setupRateLimited`, `setupWritableRateLimited`) follows the same skeleton on the `follower` (= `valueBeingBuilt()`):

1. **Naming + ownership.** Sets `follower.avName` to `"buffered (leader)"` (or `"rate limited (…)"`) if unset, and `follower.owner` to the leader if unset. The `owner` field is the buffer's **single strong reference to the leader**, keeping it alive as long as the buffer is.
2. **Weakly-held setter.** A `WriteValue` setter (`follower.makeSetter()`) is wrapped in a [`WeakCleanupWithRunnable`](../../utils/WeakCleanupWithRunnable.md). The follow-listener reaches the setter only via `followerRef.get()` — so **the listener does not keep the buffer alive**. Once the buffer is garbage-collected, `get()` returns `null` and the listener early-returns.
3. **The follow-listener.** A `ValueListener cl` is registered on both the leader and the leader's `validity()`. On fire: if `setter==null` return (buffer gone); else if the leader is valid, push `leader.getValidOrThrow()` into the buffer via `setter.set`/`accept` **inside `Piles.withShouldDeepRevalidate(false)`** (the follow-write must not trigger a deep-revalidation cascade); if the leader is invalid (or throws `InvalidValueException`), `setter.permaInvalidate()` the buffer.
4. **Self-cleanup on GC.** `followerRef.setCleanupAction(...)` removes `cl` from the leader and its validity when the buffer is collected — so a dead buffer leaves no listeners behind on a live leader.
5. **Inheritance.** `inheritBrackets(false, leader)` (the buffer inherits the leader's brackets), `follower._setEquivalence(leader._getEquivalence())` (same equivalence relation, so "changed?" is judged identically).
6. **Prime + seal.** `cl.runImmediately(true)` seeds the initial value, then `seal(...)`. Read-only variants call bare `seal()`; writable variants seal **with a write-redirect** (next section).

## Read-only vs writable

- **Read-only** (`setupBuffer`, `setupWeakBuffer`, `setupRateLimited`): sealed with no redirect — the buffer forbids direct writes and only ever changes by following the leader.
- **Writable** (`setupWritableBuffer`, `setupWritableWeakBuffer`, `setupWritableRateLimited`): additionally `follower._addCorrector(leader::applyCorrection)` (writes are corrected the same way the leader would correct them), and seals with a redirect: `newValue -> followerSetter.set(leader.set(newValue))`. So **a write to the buffer first writes the leader, then stores the leader's (possibly corrected/redirected) result back into the buffer** — the leader stays the source of truth.

## Weak buffers

`setupWeakBuffer` / `setupWritableWeakBuffer` differ only in that the leader is held through a `WeakReference` (and `owner` is set to that weak reference, *not* a strong one) — so the buffer does **not** keep the leader alive. Consequences, all handled in the listener:
- The listener null-checks both `weakLeader.get()` and `weakValid.get()` and bails if either is gone.
- If the leader has been collected, a writable weak buffer's write-redirect falls back to writing the buffer directly (`followerSetter.set(newValue)`) since there's no leader to forward to.
- The corrector also null-checks the weak leader before applying its correction.

Use a weak buffer when the buffer should be allowed to outlive interest in the leader without pinning it.

## Rate-limited buffers

`setupRateLimited` / `setupWritableRateLimited` are buffers whose follow-listener is a `ValueListener.rateLimited(coldStartTime, coolDownTime, …)`, so the buffer is throttled: it doesn't track every leader change, only as fast as the rate limit allows. They additionally `follower.transferFrom(leader, true)` up front, and the writable variant `bequeathBrackets` to the follower when the leader is an `AbstractReadListenDependency`.

**Known limitation (author TODO):** `setupWritableRateLimited` carries the in-source note *"Invalidating the buffer directly does not work yet"* — see the author-flagged uncertainty in [possible-bugs.md](../../../possible-bugs.md), not a logged defect.

## `validBuffer` is a different animal

`Piles.validBuffer` / `writableValidBuffer` are listed near the buffers but are **not** part of this `SealPile` family: they produce an **`Independent`** (not a `SealPile`) that retains the *last valid* value of the leader and stays valid even when the leader goes invalid (whereas the buffers here `permaInvalidate` when the leader is invalid). That setup lives on the independent-builder path, not in `ISealPileBuilder`; the writable form also accepts a `Function<Consumer,Consumer> defer` to defer redirected writes. See the [Piles index](_index.md) "Buffers" entry.

## Recipe

```java
// Decouple `expensive` from a noisy leader's transactions, read-only:
ReadListenDependency<T> snapshot = Piles.buffer(leader);

// Writable mirror that forwards writes to the leader:
SealPile<T> mirror = Piles.writableBuffer(leader);
mirror.set(x);            // -> leader.set(x), then stores the result back

// Throttle a fast-changing leader to at most one update per coolDown:
ReadListenDependency<T> throttled = Piles.rateLimited(leader, coldStartMs, coolDownMs);
```

## Caveats

- A buffer **lags** the leader (it updates *after* the leader's change/transaction, by listener) — do not assume buffer and leader are observably in lock-step within a transaction; that lag is the feature.
- Because following uses the leader's **equivalence relation**, a leader change the equivalence deems "no change" won't propagate to the buffer.
- The buffer holds the leader strongly via `owner` (non-weak variants) — a buffer keeps its leader alive. Use the weak variants to avoid that.
- Follow-writes run under `withShouldDeepRevalidate(false)`; don't rely on a buffer update to kick off deep-revalidation downstream.
