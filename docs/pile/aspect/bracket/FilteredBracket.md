# `pile.aspect.bracket.FilteredBracket`

A [`ValueBracket`](ValueBracket.md) decorator that applies the wrapped bracket only to held values matching a predicate (e.g. `nopOnNull` — skip null values).

Source folder: `src`. File: `pile/aspect/bracket/FilteredBracket.java`.

`FilteredBracket<E, O>` wraps a `back` bracket plus two predicates, `openFilter` and `closeFilter`. When a predicate rejects the held value, that side becomes a no-op that *keeps the bracket and keeps the value* (it does not discard either). It is built via the [`ValueBracket`](ValueBracket.md) decorator methods `filtered(...)` / `nopOnNull*`, not usually constructed directly.

See also the package [_index.md](_index.md), the sibling decorator [`AugmentedBracket`](AugmentedBracket.md), the [overview](../../../overview.md), and [concepts](../../../concepts/).

## How the predicate gates open

`open(value, owner)`:
- if `nopOpen` (the wrapped bracket's `openIsNop`, cached at construction) → return `true` (no-op, keep bracket);
- else if `openFilter != null && !openFilter.test(value)` → the value is filtered out → return `true` (skip the effect, **keep the bracket**, do not become obsolete);
- else delegate to `back.open(value, owner)`.

A `null` `openFilter` means "no open filter" — every value passes.

## How close knows whether open ran (the symmetry)

The point of a filtered bracket is that **`close` must only fire when the matching `open` actually fired** — otherwise you'd close an effect that was never opened. The held value is the same on open and close, so `close` re-tests the **`openFilter`** (not the `closeFilter`) to reconstruct whether `open` ran:

`close(value, owner)`:
- if `nopClose` (wrapped `closeIsNop`) → return `false` (no-op; don't keep the value reference on this bracket's account);
- else if `closeFilter != null && !openFilter.test(value)` → return `true` (skip close, **keep the value reference**);
- else delegate to `back.close(value, owner)`.

So `close` is gated by **`openFilter.test(value)`**: if the value would have been filtered out of `open`, it is also skipped on `close`. That is the symmetry — same predicate, same value, same decision. (On the booleans: skipped-`open` returns `true` = keep-bracket; skipped-`close` returns `true` = keep-value-reference. See [`ValueBracket`](ValueBracket.md) § the open/close contract for why those two `true`s mean different things.)

## The `nopOnNull*` factories

From [`ValueBracket`](ValueBracket.md), all built on `filtered(open, close)`:
- `filtered(filter)` — same predicate for both sides (`openFilter == closeFilter`); the common, symmetric case.
- `nopOnNull` — `filtered(IS_NOT_NULL, IS_NOT_NULL)`; skip both open and close when the held value is null. Used by `REF_COUNT_BRACKET` and the queued refcount brackets.
- `nopOnNullOpen` — `filtered(IS_NOT_NULL, null)`; **see gotcha below.**
- `nopOnNullClose` — `filtered(null, IS_NOT_NULL)`; **see gotcha below — currently throws NPE on close.**

## `filtersFirst` and predicate fusion

`filtersFirst` pushes filtering ahead of the wrapped bracket and, when the wrapped bracket is itself a `FilteredBracket`, **fuses** the two layers into one: the open filters are AND-ed (`Functional.conjunction`), likewise the close filters, with `null` (= "no filter") treated as identity. A neat optimization: if both layers use the same predicate for open and close, the fused close filter reuses the fused open filter object instead of building a second conjunction. If the wrapped bracket's own `filtersFirst` returns itself unchanged, this returns `this`. `ValueOnly.filtersFirst` is the same logic preserving the `ValueOnlyBracket` type.

## The `ValueOnly` subclass

`FilteredBracket.ValueOnly<V>` extends `FilteredBracket<V, Object>` and implements [`ValueOnlyBracket<V>`](ValueOnlyBracket.md) — the filtered wrapper for owner-agnostic brackets, with `getWrapped` / `sameFilters(...)` narrowed to `ValueOnlyBracket<V>`. The `<E>`/owner-agnostic factory chains produce these.

## Pass-through metadata

`isInheritable`, `openIsNop`/`closeIsNop` (the cached `nopOpen`/`nopClose`), and `canBecomeObsolete` all delegate to / mirror the wrapped bracket. Filtering does not change the bracket's declared shape. `sameFilters(newBack)` rewraps a *different* back bracket with the same two predicates.

## Caveats & gotchas

- **`close` deliberately tests `openFilter`, not `closeFilter`.** This is by design (symmetry: close fires iff the corresponding open fired). When you build with `filtered(filter)` or `nopOnNull` the two predicates are equal, so this is invisible. It only surprises you with **asymmetric** filters.
- **Filtered-out is keep-everything, not discard.** A rejected `open` returns `true` (bracket stays installed); a rejected `close` returns `true` (value reference retained). Filtering suppresses the *effect*, never the bracket or the value.
- **`nopOnNullClose` (asymmetric, `openFilter==null`) throws NPE on close.** Because `close` evaluates `closeFilter != null && !openFilter.test(value)` and here `closeFilter` is non-null while `openFilter` is null → `NullPointerException`. See tech debt.
- **`nopOnNullOpen` (asymmetric, `closeFilter==null`) silently skips its close gate.** With `closeFilter==null`, the `close` guard short-circuits and `back.close` always runs — even for null values that `open` skipped — so the open/close symmetry is lost for this factory.

## Tech debt / warts

- The `close` null-guard keys off the wrong predicate: it should guard on `openFilter != null` (the predicate it actually tests), not `closeFilter != null`. As written, asymmetric filters where only one side is non-null misbehave: `nopOnNullClose` NPEs, `nopOnNullOpen` loses its symmetry. The symmetric factories (`filtered(filter)`, `nopOnNull`) are unaffected, which likely masks the bug in practice.
