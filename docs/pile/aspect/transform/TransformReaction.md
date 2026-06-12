# `pile.aspect.transform.TransformReaction`

The reaction a [`TransformHandler`](TransformHandler.md) returns for one value, telling the transform machinery how that value should respond to a transform request (ignore, restore, propagate, mutate, replace, or recompute).

Source folder: `src`. File: `pile/aspect/transform/TransformReaction.java`.

An abstract `Runnable`; each instance carries a `ReactionType` (`getType`) and, for the two value-changing kinds, the closure that actually transforms the value (`run` executes it). It is part of the **transform mechanism** — propagating a covariant transformation from a value to its dependers; per the package index this whole feature is **rudimentary/immature** (see [`_index.md`](_index.md) and the `TransformableValue` javadoc warning). The live driver is [`PileImpl`](../../impl/PileImpl.md)'s transform methods.

## The reaction kinds (`ReactionType`)

`react(...)` returns one of seven kinds. Each value in the (transitive depender) graph gets its own reaction; the set is collected first, then applied. What each makes the value do:

- **`IGNORE`** — do nothing; the value stays as-is (it may still go invalid/recompute later if a dependency changes). **The request is NOT propagated to this value's dependers** — `IGNORE` (and `UNCHANGING`) prune the propagation subtree. (Also the fallback when a handler returns `null`: `collectTransformReactions` substitutes `IGNORE`, `TransformableValue.java`.)
- **`UNCHANGING`** — also don't propagate, but **restore the pre-transform value afterwards**: the value (and, if it was invalid, its revalidation) is captured and re-applied in an `afterTransform` step, with auto-validation suppressed in the meantime. Net effect: this value is held constant across the transform even though something upstream changed. Note the `switch` case **falls through** from `UNCHANGING` into `IGNORE` (no `break` at ), which is deliberate — both then release the transform transaction.
- **`JUST_PROPAGATE_NO_TRANSACTION`** — don't transform this value, but **do forward the request to its dependers**; do not open a regular transaction around it.
- **`JUST_PROPAGATE_WITH_TRANSACTION`** — like the above, but **open a `transaction`** on this value for the duration (so its dependers' changes are batched / it isn't observed mid-flux).
- **`MUTATE`** — transform by **mutating the held value in place** (same reference). See [`MutateReaction`](MutateReaction.md). Propagates to dependers.
- **`REPLACE`** — transform by **computing a new value** and storing it. See [`ReplaceReaction`](ReplaceReaction.md). Propagates to dependers.
- **`RECOMPUTE`** — don't transform; **invalidate and recompute** this value after the transform. Also the default when `getTransformHandler` returns `null` for the value.

## Subtypes — `UntypedReaction` vs `TypedReaction`

- **`UntypedReaction`** (nested in [`TransformHandler`](TransformHandler.md)) wraps the four kinds that carry no transform closure and need no `<E>`: `IGNORE`, `UNCHANGING`, `RECOMPUTE`, `JUST_PROPAGATE_NO_TRANSACTION`, `JUST_PROPAGATE_WITH_TRANSACTION`. These are exposed as **singletons** / static factories on `TransformReaction` (`IGNORE`, `RECOMPUTE`, `UNCHANGING`, `JUST_PROPAGATE_*`, and the `ignore`/`recompute`/… methods, `TransformReaction.java`). (The class-doc on `UntypedReaction` says "four standard reactions" but there are actually five singletons — a stale comment.)
- **`TypedReaction<E>`** (also nested in `TransformHandler`) is the base for the two value-changing kinds and carries `apply(E in)` (the actual transform) plus optional `cancelCode` run via `cancel` when the transform is skipped because the value went invalid. Its two concrete subtypes:
  - [`MutateReaction<E>`](MutateReaction.md) — `apply` runs a `Consumer<E>` on the value in place; guards that the value is still the *expected* valid reference, else `cancel`s and throws `InvalidValueException`.
  - [`ReplaceReaction<E>`](ReplaceReaction.md) — `apply` runs a `Function<E,E>` and returns the new value to store.

## `fast` and `run`

- `fast` (default `true`) — whether the transform code is cheap enough to run **sequentially on the triggering thread**; otherwise it is dispatched to a parallel thread. The two `TypedReaction`s override it from a constructor flag (default **`false`** there, `MutateReaction.java`, `ReplaceReaction.java`). So `MutateReaction`/`ReplaceReaction` default to *async* even though the base class default is *sync*.
- `run` — no-op on the base / `UntypedReaction` (these kinds don't run transform code). The `TypedReaction`s override it to name the thread and call back into `value.runTransform(this)`.

## How the kinds are applied (in `PileImpl` / `TransformableValue`)

Dispatch is split across two switches; reading both is the only way to see what a kind does:

1. **Collect phase** — `TransformableValue.collectTransformReactions` asks each handler, records the reaction, and **decides propagation**: `IGNORE`/`UNCHANGING`/`RECOMPUTE` stop here (release the transform transaction); `JUST_PROPAGATE_*`/`MUTATE`/`REPLACE` recurse into the dependers.
2. **Apply phase** — `TransformableValue.transform` switches per collected reaction: `IGNORE`/`UNCHANGING`/`JUST_PROPAGATE_NO_TRANSACTION` do nothing here; `JUST_PROPAGATE_WITH_TRANSACTION` opens a `transaction`; `RECOMPUTE` opens a transaction and calls `runTransformRevalidate`; `MUTATE`/`REPLACE` open a transaction and queue the reaction onto the sync (if `fast`) or async job list.
3. **`PileImpl.runTransform(TypedReaction)`** executes a `MUTATE`/`REPLACE` only. It reads the **old value** snapshot under `mutex`, and:
   - `MUTATE`: if `oldValid`, calls `reaction.apply(oldValue)` then `valueTransformMutated`; else `reaction.cancel`.
   - `REPLACE`: if `oldValid`, `apply`s to get the result and stores it via `__conditionalSecretSet(result, oldValue)` (a quiet set guarded on the old value still matching, ), then `valueTransformMutated`; else `cancel`.
   - The non-typed kinds (`IGNORE`/`UNCHANGING`/`RECOMPUTE`/`JUST_PROPAGATE_*`) **throw `IllegalStateException` here** — they must never reach `runTransform`; they are handled in the apply-phase switch above.

## Caveats & gotchas

- **`null` reaction ⇒ `IGNORE`; `null` handler ⇒ `RECOMPUTE`** — two different defaults, easy to confuse.
- **`IGNORE` and `UNCHANGING` prune propagation** — dependers of an ignoring value are *not* asked to transform. If you want the request to reach dependers without transforming this value, use a `JUST_PROPAGATE_*` kind instead of `IGNORE`.
- **Default async for typed reactions:** the base `fast` is `true` but `MutateReaction`/`ReplaceReaction` default their flag to `false`, so they run on a parallel thread unless you pass `fast=true`. Mind thread-safety of your transform closures.
- **Transform runs off the old/pre-transaction value**, not the current value (`runTransform` reads `oldValid`/`oldValue`). If the value isn't `oldValid`, the typed transform is silently `cancel`ed rather than applied.
- `InvalidValueException` thrown by a typed `apply` is caught and merely `printStackTrace`d inside `runTransform` — failures are swallowed, not surfaced.
- Whole feature is **immature**: no concurrent transforms over overlapping graph regions, transform can't change mid-flight (`TransformableValue.java`, package [`_index.md`](_index.md)).

## Tech debt / warts

- `UntypedReaction`'s javadoc ("four standard reactions") is stale — there are five untyped singletons.
- The `UNCHANGING`→`IGNORE` fall-through in `collectTransformReactions` is correct but unmarked (no `// fall through` comment), which reads like a missing `break`.
- Large blocks of commented-out transaction code remain in `beginTransformTransaction`/`endTransformTransaction`, evidence of in-progress design.

## Related

- [`TransformHandler`](TransformHandler.md) — produces these reactions; defines `UntypedReaction`/`TypedReaction`.
- [`MutateReaction`](MutateReaction.md) · [`ReplaceReaction`](ReplaceReaction.md) — the two value-changing subtypes.
- [`PileImpl`](../../impl/PileImpl.md) — the driver (`runTransform`, transform transactions). · package [`_index.md`](_index.md) · [overview](../../../overview.md) · concepts: [`../../../concepts/`](../../../concepts/).
