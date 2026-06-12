# `pile.builder.FulfillInvalid`

A control-flow `RuntimeException` a recompute callback throws to make the framework call `Recomputation.fulfillInvalid` — i.e. "finish this recomputation by marking the value invalid."

Source folder: `src`. File: `pile/builder/FulfillInvalid.java` (~56 lines).

## What it's for

When you supply a recomputer to a builder ([`IPileBuilder`](IPileBuilder.md) / [`AbstractPileBuilder`](AbstractPileBuilder.md)), the normal way to finish is to call `re.fulfill(value)` on the handed `Recomputation`. If instead the callback decides the value cannot be computed (a dependency is unusable, an input is out of range, etc.) it can simply **throw `FulfillInvalid`**. The builder's recomputer wrapper catches it and calls `re.fulfillInvalid`, leaving the pile invalid rather than producing a value (`AbstractPileBuilder.java`, and the `recompute(Supplier)` adapter at `IPileBuilder.java`).

This is purely a convenience: it lets deeply-nested callback code bail out without threading the `Recomputation` reference down to the bail-out point.

## Key members

- Four constructors mirroring the standard `RuntimeException` set (no-arg, `String`, `String`+`Throwable`, `Throwable`).
- Static **`r(...)`** thrower helpers (`r`, `r(String)`, `r(String, Throwable)`, `r(Throwable)`) — each declares return type `FulfillInvalid` but **always throws** and never returns. The return type is a convenience so you can write `throw FulfillInvalid.r(...)` in a context that wants an expression (e.g. the false-branch of a ternary), and the compiler still sees a thrown/returned value.

## Salient / surprising behavior

- **Caught only by the builder's recomputer machinery**, not by Pile broadly. It works because every `MyRecomputer.accept` variant (and the `recompute(Supplier)` adapter) wraps the user callback in a `try/catch(FulfillInvalid)`. Throwing it outside a builder-installed recompute callback just propagates as an ordinary unchecked exception.
- It is **flow control via exception** — semantically equivalent to calling `re.fulfillInvalid` and returning, not an error condition. There is a parallel sentinel-return path for staged functions: returning `Piles.FULFILL_INVALID` does the same thing (see [`AbstractPileBuilder`](AbstractPileBuilder.md), the `MyRecomputer` family § sentinel return values).

## Caveats & gotchas

- Throwing it after the recomputation is already finished/cancelled has no special handling here; the surrounding wrappers generally ignore post-finish exceptions, but don't rely on `FulfillInvalid` to "un-fulfill."
- Because it is unchecked, nothing forces callers to be inside a recompute callback; misuse won't be flagged at compile time.

## Tech debt / warts

- None of note. Small, self-explanatory helper. The `r(...)` "returns never" idiom is intentional, not a bug.

## Related

- [builder index](_index.md) · [`IPileBuilder`](IPileBuilder.md) · [`AbstractPileBuilder`](AbstractPileBuilder.md) (the catch sites and the `Piles.FULFILL_INVALID` sentinel) · [overview](../../overview.md).
- The recompute lifecycle (`Recomputation.fulfillInvalid`) lives in `pile.aspect.recompute` — *doc pending*; see [concepts/transactions.md](../../concepts/transactions.md) for the recompute/validity model.
