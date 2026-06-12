# `pile.aspect.bracket.AugmentedBracket`

A `ValueBracket` decorator that wraps another bracket and runs an extra `BiConsumer` **before** the wrapped `open` and **after** the wrapped `close`.

Source folder: `src`. File: `pile/aspect/bracket/AugmentedBracket.java`.

`AugmentedBracket<E, O>` holds a wrapped bracket `back` plus two optional consumers, `preOpen` and `postClose`. It is the bracket created by [`ValueBracket`](ValueBracket.md)'s `beforeOpening(...)` / `beforeClosing(...)` decorator methods — see the ValueBracket decorator catalogue. For values matching a predicate use [`FilteredBracket`](FilteredBracket.md); for the bracket interface and its open/close contract see [`ValueBracket`](ValueBracket.md). Package: [_index.md](_index.md) · [overview](../../../overview.md) · [concepts](../../../concepts/).

## What it augments / ordering

The decoration is asymmetric in timing:

- **`open`** — runs `preOpen.accept(value, owner)` **first** (if non-null), then returns `back.open(...)`. So the extra behavior is genuinely *before opening*; the return (keep-the-bracket flag) is the wrapped bracket's.
- **`close`** — runs `back.close(...)` **first**, captures its return, then runs `postClose.accept(value, owner)` (if non-null), and returns the wrapped bracket's keep-the-value flag. So the extra behavior is *after closing*.

The augmenting consumers never affect the booleans — both `open`'s keep-bracket result and `close`'s keep-value result are passed straight through from `back`.

## Metadata forwarding

All metadata is delegated to `back`, except the two nop flags which also account for the added consumer:

- `isInheritable` — forwarded unchanged.
- `canBecomeObsolete` — forwarded unchanged.
- `openIsNop` — `back.openIsNop && preOpen == null`. Adding a `preOpen` makes open no longer a nop.
- `closeIsNop` — `back.closeIsNop && postClose == null`. Adding a `postClose` makes close no longer a nop.

## `filtersFirst`

`filtersFirst` pushes any filtering in the wrapped bracket ahead of this augmentation, preserving identity where possible:

- Asks `back.filtersFirst`. If it returns the same instance (nothing to reorder), returns `this` unchanged.
- Otherwise rebuilds an `AugmentedBracket` around the reordered wrapped bracket. If that reordered bracket is itself a [`FilteredBracket`](FilteredBracket.md), the augmentation is moved *inside* the filter: it re-wraps `cast.getWrapped` and re-applies the same filters via `cast.sameFilters(...)`, so the filter ends up outermost (the augmenting consumers then only fire for values that pass the filter).

## `ValueOnly` nested subclass

`AugmentedBracket.ValueOnly<V>` extends the decorator for the owner-agnostic [`ValueOnlyBracket`](ValueOnlyBracket.md) case. It adds a `Consumer<V>`-based constructor (adapting each `Consumer` to a `(v, o) -> consumer.accept(v)` that ignores the owner, `AugmentedBracket.java`) and overrides `filtersFirst` with the `ValueOnlyBracket` / `FilteredBracket.ValueOnly` variants so the reordered result stays a `ValueOnlyBracket`.

## Caveats & gotchas

- **`preOpen` runs even if `back.open` later returns `false`** (bracket obsolete). The pre-open side effect is not conditional on the wrapped open succeeding/remaining.
- **`postClose` runs after `back.close`**, so it sees the world as the wrapped bracket left it; it cannot influence the keep-the-value return.
- Like all brackets, `open`/`close` run under the owner's `mutex` — keep `preOpen`/`postClose` cheap and non-reentrant (see [`ValueBracket`](ValueBracket.md) on the mutex-held caveat).
- A null consumer is the idiomatic "no extra behavior on this side" — not a bug; it just means that side adds nothing and stays a nop if the wrapped side was.

## See also

- [`ValueBracket`](ValueBracket.md) — interface, open/close contract, and the `beforeOpening`/`beforeClosing` factories that build this.
- [`FilteredBracket`](FilteredBracket.md) — the predicate decorator that `filtersFirst` cooperates with.
- [`ValueOnlyBracket`](ValueOnlyBracket.md) — the owner-agnostic refinement the `ValueOnly` subclass implements.
