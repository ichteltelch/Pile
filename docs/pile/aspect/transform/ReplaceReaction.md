# `ReplaceReaction`

A `TransformReaction` (type `REPLACE`) that **replaces** the held value with a freshly computed one (`apply` returns a new value rather than mutating the old in place).

Source folder: `src` — `pile.aspect.transform.ReplaceReaction`.

See the package [_index.md](_index.md), the [overview](../../../overview.md), and the [transactions concept](../../../concepts/transactions.md). Siblings: [TransformReaction.md](TransformReaction.md), [MutateReaction.md](MutateReaction.md). Driver: [PileImpl.md](../../impl/PileImpl.md).

## What it is

A concrete `TransformHandler.TypedReaction<E>` whose `getType` is fixed to `ReactionType.REPLACE`. It carries:
- the `TransformableValue<E> value` it acts on,
- a `Function<? super E, ? extends E> transform`,
- a `fast` flag (returned by `fast`),
- an optional `cancelCode` (inherited from `TypedReaction`).

`apply(in)` simply returns `transform.apply(in)` — a **new** value computed from the old one. This is the key contrast with `MutateReaction`, whose transform mutates the existing object in place and returns nothing meaningful to be re-stored.

## Key methods

- `apply(E in)` — returns `transform.apply(in)`; the result becomes the new held value.
- `cancel` — inherited; runs `cancelCode` if non-null. Invoked when the transform is skipped because the value was invalid (see below).
- `fast` — returns the constructor `fast` flag.
- `run` — temporarily renames the current thread to `"Replace transform: "+value`, then calls `value.runTransform(this)`, restoring the old name in a `finally`. This is how the reaction drives itself into the value's transform machinery.

Four constructors cover the cross product of `fast` (default `false`) and `cancelCode` (default `null`).

## How `PileImpl.runTransform` invokes it (REPLACE case)

`PileImpl.runTransform(TypedReaction)` handles `REPLACE` at `PileImpl.java`:

1. It first takes the value's transform mutex, waiting until no other `transformThread` is active, then claims `transformThread = currentThread` (so transforms on one value are **serialized**, not concurrent).
2. It snapshots `oldValid` / `oldValue` under `mutex`.
3. If the snapshot was **valid**: it computes `result = reaction.apply(ovalue)`, then — re-checking it still owns the transform thread — calls `__conditionalSecretSet(result, ovalue)` and finally `valueTransformMutated`.
4. If the snapshot was **invalid**: it calls `reaction.cancel` instead (the transform code never runs).
5. `InvalidValueException` from `apply` is swallowed with a stack-trace print.

`__conditionalSecretSet(v, oldMustBe)` installs the new value **without** triggering transactions, `ValueEvent`s, or depender recomputations, and only if the value hasn't changed underneath it: it returns early if `oldMustBe != oldValue || !oldValid`, and also short-circuits if the current value is already equivalent (`equivalence.test`). On a real change it runs `closeBrackets` / sets `__value` / `openBrackets`, then fires `fireValueChange` after releasing the mutex. So the replace is a quiet, compare-and-set-style swap guarded against concurrent change.

## How it differs from `MutateReaction`

| | `ReplaceReaction` | `MutateReaction` |
|---|---|---|
| `getType` | `REPLACE` | `MUTATE` |
| `apply` result | a **new** value, re-stored via `__conditionalSecretSet` | mutates in place; result is **not** re-stored |
| effect in `runTransform` | `apply` → `__conditionalSecretSet(result, ovalue)` → `valueTransformMutated` | `apply(ovalue)` (side-effecting) → `valueTransformMutated` |

Both call `cancel` when the value was invalid at snapshot time, and both go through the same serialized transform-mutex path.

## Caveats & gotchas

- **Invalid value ⇒ no transform, just cancel.** If the value is invalid when the transform runs, `apply` is never called; only `cancelCode` runs. Don't put essential side effects solely in `transform`.
- **Silent swallow of `InvalidValueException`** from `apply` (stack-trace only, `PileImpl.java`) — the value is then left unchanged.
- **Quiet update.** `__conditionalSecretSet` deliberately suppresses transactions/events/recomputation and fires only a value-change; the normal validity-propagation machinery is bypassed.
- **Compare-and-set guard.** If `oldValue` changed between snapshot and store, the store is silently dropped — the computed `result` is discarded.
- **Thread-name side effect.** `run` renames the current thread for the duration; purely cosmetic (debugging aid), restored in `finally`.

## Tech debt / warts

- Per the package index, the whole transform mechanism is **rudimentary/immature**: no concurrent transforms over overlapping graph regions, and the transformation can't be changed mid-flight.
- `apply` errors are swallowed rather than surfaced.
