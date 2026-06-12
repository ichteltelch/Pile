# `pile.aspect.transform.MutateReaction`

A `TransformReaction` (type `MUTATE`) that transforms a value by mutating it **in place**, keeping the same object reference.

Source folder: `src`. File: `pile/aspect/transform/MutateReaction.java`.

It is the in-place sibling of [`ReplaceReaction`](ReplaceReaction.md) (which computes a *new* value). Both extend `TransformHandler.TypedReaction<E>`; the type tag (`getType`) is what `PileImpl.runTransform` switches on — see [`TransformReaction`](TransformReaction.md) for the reaction kinds and the package [`_index.md`](_index.md) for the mechanism.

## What it's for

A `TransformHandler` returns a `MutateReaction` when the requested transform should be expressed as a `Consumer<? super E>` that mutates the held value (e.g. edit fields of a mutable object) rather than swapping in a replacement. Because the reference is unchanged, downstream machinery that compares by identity sees the same object.

## Key members

- **`transform`** — the `Consumer<? super E>` applied to the held value.
- **`apply(E in)`** — runs `transform.accept(in)` and returns `in` unchanged (the same reference). Guarded; see below.
- **`cancel`** (inherited from `TypedReaction`) — runs the optional `cancelCode` `Runnable` passed to the constructor; called when the transform is *not* applied because the value went invalid.
- **`fast`** — returns the constructor `fast` flag (default `false`); tells the runner whether the transform code is cheap enough to run inline on the triggering thread.
- **`run`** — entry point: sets the thread name to `"Mutate transform: <value>"` and calls `value.runTransform(this)`, which dispatches back into `apply`/`cancel` (see below).
- Constructors capture an **expected snapshot** at construction: if `value.isValid`, `expectedValue = value.getValidOrThrow` and `expectedValid = true`.

## The identity guard (salient)

`apply` does **not** blindly run the transform:

```
if (expectedValid && in == expectedValue) transform.accept(in);
else { cancel; throw new InvalidValueException; }
```

The check is reference identity (`==`) against the value captured at construction. If the incoming value isn't the very object that was present when the reaction was built (or no valid value was captured), the transform is **skipped**, `cancel` runs, and `InvalidValueException` is thrown. This is the guard against mutating a stale/changed value.

## How `PileImpl.runTransform` invokes it (the `MUTATE` case)

`runTransform(TypedReaction)` serializes on the transform mutex (one transform thread at a time), then snapshots the **old value** under `mutex`: `ovalid = oldValid`, `ovalue = oldValue`. For `MUTATE`:

- If `ovalid`: call `reaction.apply(ovalue)`, then `valueTransformMutated`. The return value of `apply` is **discarded** — unlike the `REPLACE` case, there is no `__conditionalSecretSet`, precisely because the reference doesn't change.
- If `!ovalid`: call `reaction.cancel` and do nothing else.
- A thrown `InvalidValueException` is caught and merely `printStackTrace`d, so a failed mutate is swallowed after the stack trace — see gotchas.

Note the value passed in is `oldValue` (the pre-transaction snapshot), not the live `__value`. The reaction's identity guard (`in == expectedValue`) therefore also asserts that `oldValue` is still the object captured at construction.

## Caveats & gotchas

- **Snapshot races.** `expectedValue` is captured when the reaction is constructed; `runTransform` applies it to `oldValue` later. If the value object changed in between, the `==` guard fails and the mutate silently cancels (only a stack trace if it gets as far as throwing). This is by design but easy to trip over.
- **Failures are printed, not propagated.** Both the guard failure (via `apply` throwing) and any exception are caught in `runTransform` and only logged. Callers get no signal that the mutate didn't happen.
- **Mutation must be in place.** The `Consumer` must mutate the existing object; returning/swapping has no effect since `apply`'s return value is ignored in the MUTATE path. Use [`ReplaceReaction`](ReplaceReaction.md) when a new value is needed.
- The transform subsystem as a whole is **rudimentary/immature** (no concurrent transforms over overlapping graph regions, transform can't change mid-flight) — see the package [`_index.md`](_index.md) and the [overview](../../../overview.md).

## Related

- [`ReplaceReaction`](ReplaceReaction.md) — the replace-with-new-value sibling.
- [`TransformReaction`](TransformReaction.md) — reaction kinds and `TypedReaction`/`cancel` contract.
- [`PileImpl`](../../impl/PileImpl.md) — `runTransform` drives the dispatch.
- [package index](_index.md) · [overview](../../../overview.md) · [concepts](../../../concepts/)
