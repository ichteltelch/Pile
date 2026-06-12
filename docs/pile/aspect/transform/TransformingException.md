# `TransformingException`

Unchecked exception signalling that an operation can't proceed because a transform transaction is in progress. Source folder: `src`.

Package: `pile.aspect.transform`. Up: [package index](_index.md) · [overview](../../../overview.md) · [concepts/transactions.md](../../../concepts/transactions.md).

## What it's for

A bare `RuntimeException` subclass (no fields, no constructors, no message) thrown when something is attempted during a transform that mustn't happen while it is ongoing — chiefly starting a *second*, overlapping transform transaction, or a `set`/read that opted into failing rather than blocking. It is the failure mode behind [`BehaviorDuringTransform.THROW_TRANSFORMINGEXCEPTION`](BehaviorDuringTransform.md).

## Checked vs unchecked

**Unchecked** — extends `RuntimeException`, so callers need not declare or catch it. The aspect methods that can raise it ([`TransformableValue`](TransformableValue.md) `checkForTransformEnd` / `checkForTransformEnd(BehaviorDuringTransform)`) document it only as `@throws TransformingException Possibly`. Contrast with the *blocking* path, which instead waits and can throw the **checked** `InterruptedException` (e.g. `beginTransformTransaction`).

## Where it's thrown

Both throw sites are in [`PileImpl`](../../impl/PileImpl.md):

- `beginTransformTransaction` — `PileImpl.java`: when a transform transaction is already in progress more deeply than allowed (`transformTransactions>1`), starting another fails fast rather than blocking forever.
- `checkForTransformEnd(BehaviorDuringTransform)` — `PileImpl.java`: while a transform thread/transaction is active and the requested behavior is `THROW_TRANSFORMINGEXCEPTION`, the operation throws instead of waiting (`NOP` returns immediately; `BLOCK` waits on the transform mutex). The thread that *started* the transform is exempt (it returns early), so a transform's own code won't trip on it.

## Where it's caught

Nowhere in Pile — `find_references` turns up only the two throw sites and the `TransformableValue` `@throws` declarations. It propagates to the caller of `set`/read/`beginTransformTransaction`. Catching it (if you do) is the caller's responsibility.

## Caveats & gotchas

- Carries no message or cause; a stack trace is the only diagnostic.
- Per [`TransformableValue`](TransformableValue.md), avoid calling `set` / `permaInvalidate` from code running *during* a transform — those are exactly the operations that block or (under `THROW_TRANSFORMINGEXCEPTION`) raise this.
- The transform mechanism is rudimentary (no concurrent transforms on overlapping graph regions) — see the [package index](_index.md). This exception is part of how that limitation is enforced at runtime.
