# `MockBlock`

A try-with-resources scope object (a [`SafeCloseable`](SafeCloseable.md)) that runs open-logic on construction and close-logic on `close`; the framework returns one from its `with*` methods to scope a thread-local override (set on enter, restore on close).

Source folder: `src` (package `pile.aspect.suppress`).

Up: [suppress index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md). Siblings: [`SafeCloseable`](SafeCloseable.md) (the no-checked-exception base), [`Suppressor`](Suppressor.md) (the release-to-undo handle). Used by: [`Recomputations`](../recompute/Recomputations.md) (`with*`).

## What it's for

Java has no user-defined block constructs, so `MockBlock` fakes one via try-with-resources:

```java
try (MockBlock _ignored = someMethod) {
    content;
}
```

The constructor calls `open` immediately; `close` calls `close_impl` at end of block. Subclasses fill in the two halves. The canonical use is to **scope a thread-local override**: `open` (or the construction site) installs a new value and remembers the old one, `close_impl` restores the old one — see [`Recomputations.withDependencyRecorder`](../recompute/Recomputations.md) and `Piles.withShouldDeepRevalidate`.

## Key members by purpose

- **Constructors** — `MockBlock` records the current thread and calls `open`. `MockBlock(Runnable alsoOpen)` additionally runs `alsoOpen` as part of opening.
- **`open` / `close_impl`** (abstract, `protected`) — the two halves of the block; subclasses implement them.
- **`closeOnly(Runnable r)`** (static) — make a block with empty `open` and `r` as `close_impl`. The common "I only need to run something on close" case.
- **`close`** — restores via `close_impl`; thread-guarded (see below).
- **`cancellableClose`** — returns a `CancelClose` letting you move the close outside the try block and optionally `cancel` it. The javadoc itself admits no clear use case; ignore unless you need it.
- **`NOP`** (static final) — a shared do-nothing block (see below).

## Salient / surprising behavior

- **`NOP` short-circuits "no change."** When a `with*` method finds the new value equals the current one, it returns the shared `MockBlock.NOP` instead of allocating: opening installs nothing and closing does nothing. `NOP` is built with `myThread=null`, which makes its `close` an immediate no-op regardless of thread. So a redundant scope costs no allocation and is safe to close anywhere. (Example: [`Recomputations.withDependencyRecorder` returns `NOP` on a redundant install](../recompute/Recomputations.md).)
- **One-shot close, thread-bound.** `close` sets `myThread=null` before running `close_impl`, so a second `close` is a silent no-op (idempotent). It must be closed **on the same thread that opened it** — closing on another thread throws `IllegalStateException("MockBlock closed in wrong thread")`. `NOP` is exempt (its `myThread` is already `null`).
- **Open runs in the constructor.** There is no "deferred open" — building the block *is* entering it. The `alsoOpen` runnable and `open` both run before the constructor returns.

## Caveats & gotchas

- **Close LIFO.** `MockBlock`s that scope thread-locals must be closed in reverse order of opening — they restore *the value that was current when they opened*, not a sentinel. try-with-resources gives you LIFO for free; manual / `cancellableClose` use can break it. Out-of-order close of recorder blocks is detected and logged `SEVERE` by [`Recomputations`](../recompute/Recomputations.md) (it checks the thread-local still holds its value before restoring), but in general nothing enforces LIFO and a stale override can leak.
- **Don't share an instance across threads.** The open thread is captured at construction; pass the *factory call* into the worker thread, not a pre-built block (cf. transferring the ambient recomputation in [`Recomputations`](../recompute/Recomputations.md)).
- **Name the resource `_ignored`/`_`.** You never call methods on it; it exists only for its close.

## Common tasks

- **Scope a thread-local for a block:** `try (MockBlock b = X.withThing(v)) { ... }` — provided by the framework's `with*` methods, not built by hand.
- **Run cleanup on close only:** `MockBlock.closeOnly( -> cleanup)`.
- **Represent "no scope needed":** return `MockBlock.NOP`.

## Tech debt / warts

- `cancellableClose` is admitted dead weight by its own javadoc ("I can't think of any use cases"). It complicates the API (and `NOP` must override it) for a path nothing uses.
- The thread-mismatch guard throws, but the LIFO/ordering contract is unenforced here — correctness of nesting is left to callers and to per-site checks like the one in `Recomputations`.
