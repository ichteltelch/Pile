# `pile.aspect.bracket.NonreentrantBracket`

A decorator that guards a wrapped [`ValueBracket`](ValueBracket.md) so its `open`/`close` cannot run reentrantly on the same thread; a reentrant call is silently skipped.

Source folder: `src`. File: `pile/aspect/bracket/NonreentrantBracket.java`.

`NonreentrantBracket<E, O>` wraps a backing bracket (`back`) and a [`Nonreentrant`](../../utils/) token (`nr`). When the wrapped bracket's `open`/`close` would otherwise fire, the decorator first tries to *enter* the `Nonreentrant` scope; if the current thread is **already inside that scope** (i.e. this open/close is reentrant), the backing effect is **not** invoked. Build one via the `nonreentrant(Nonreentrant)` decorator on [`ValueBracket`](ValueBracket.md) (see its decorator catalogue).

## What reentry it prevents, and how

The guard is the shared `Nonreentrant nr`. `Nonreentrant` keeps a per-thread `in` flag; `nr.block_noThrow` returns a real `MockBlock` (setting the flag for the block's lifetime) on first entry, but returns the sentinel `MockBlock.NOP` when the thread is already inside the scope. The bracket uses that sentinel as its reentry test (`NonreentrantBracket.java`, `42-45`):

```java
try (MockBlock mb = nr.block_noThrow) {
    if (mb != MockBlock.NOP) {       // first (non-reentrant) entry
        return back.open(value, owner);
    }
}                                     // reentrant: back not called
```

So if running the backing `open`/`close` somehow leads back into another `open`/`close` guarded by the **same `Nonreentrant`** (on the same thread), the inner call short-circuits instead of recursing. Because the token is shared, *one* `Nonreentrant` can serialize a whole family of brackets against mutual reentry, not just one bracket against itself.

## The skipped-call return values (gotcha)

When a call is skipped as reentrant, the decorator returns the bracket's **"do nothing of consequence"** value for each side — and these follow the asymmetric `ValueBracket` return contract (see [`ValueBracket`](ValueBracket.md)):

- **`open` skipped → returns `true`** — keep the bracket installed.
- **`close` skipped → returns `false`** — do *not* request keeping the value reference.

This is deliberate: a reentrant call must neither retire the bracket nor pin the value on the strength of a no-op. It does mean a skipped `close` contributes a "drop the value reference" vote even though it did nothing.

## Nop shortcut and metadata pass-through

The constructor caches `back.openIsNop` / `back.closeIsNop` into `nopOpen` / `nopClose`. If the backing side is a guaranteed no-op, the decorator skips the guard entirely and returns immediately (`open`→`false`, `close`→`false`; `NonreentrantBracket.java`, `21-22`) — note this early `open` returns `false`, unlike the reentry-skip path which returns `true`. `isInheritable`, `openIsNop`, `closeIsNop`, and `canBecomeObsolete` all delegate to `back`.

## `filtersFirst`

Mirrors the [`ValueBracket`](ValueBracket.md) `filtersFirst` hook: it asks the backing bracket to float its filter outermost, then re-wraps. If `back.filtersFirst` surfaced a [`FilteredBracket`](FilteredBracket.md), this decorator rebuilds as `FilteredBracket(nonreentrant(inner), …)` so the **filter runs before** the nonreentrant guard rather than inside it. Otherwise it returns `this` (or a fresh wrapper around the unwrapped backing). The nested `ValueOnly` subclass does the same for [`ValueOnlyBracket`](ValueOnlyBracket.md) backings.

## `NonreentrantBracket.ValueOnly`

A subclass implementing [`ValueOnlyBracket<V>`](ValueOnlyBracket.md) (owner-agnostic) for wrapping a `ValueOnlyBracket` while preserving that refinement — used so the result is still acceptable to a sealed `SealPile`. Only `filtersFirst` differs; all open/close logic is inherited.

## Caveats & gotchas

- **Same-token serialization.** The guard is per-`Nonreentrant`, per-thread. Brackets sharing one token guard each other; brackets with distinct tokens don't. Reentry on a *different* thread is not blocked (the flag is `ThreadLocal`).
- **Skipped ≠ failure.** A reentrant call returns the no-op vote, not an exception. A skipped `close` votes "drop value ref"; a skipped `open` votes "keep bracket".
- **Asymmetric early-return.** The `nopOpen`/`nopClose` shortcut returns `false` from `open`, whereas the reentry skip returns `true`. Both are no-ops behaviorally, but the bracket-retirement vote differs.
- **No `MockBlock` import shown for the back call** — the whole effect runs inside the `try`-with-resources, so the scope flag is reset as soon as `back.open`/`close` returns; nothing the backing bracket schedules *later* is still inside the scope.

## See also

- [`ValueBracket`](ValueBracket.md) — the interface; its `nonreentrant(Nonreentrant)` decorator builds this.
- [`ValueOnlyBracket`](ValueOnlyBracket.md) — refinement preserved by the `ValueOnly` subclass.
- [`FilteredBracket`](FilteredBracket.md) — the decorator `filtersFirst` reorders ahead of the guard.
- [`Nonreentrant`](../../utils/) / `MockBlock` — the per-thread guard token and its scope handle.
- package [_index.md](_index.md) · [overview](../../../overview.md) · [concepts](../../../concepts/).
