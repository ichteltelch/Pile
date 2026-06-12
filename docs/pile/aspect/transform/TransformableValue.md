# `pile.aspect.transform.TransformableValue`

The aspect for values that can undergo a covariant **transformation** that propagates to their dependers; defines the transform protocol (`transform`, `runTransform*`, transform transactions, `checkForTransformEnd`, `getTransformHandler`) while delegating the real mechanics to [`PileImpl`](../../impl/PileImpl.md).

Source folder: `src`. File: `pile/aspect/transform/TransformableValue.java`. Interface: `TransformableValue<E> extends `[`ReadWriteValue`](../combinations/) `<E>`.

> **Rudimentary feature.** The interface javadoc and the project `README` both flag the transform mechanism as primitive: "It does what I need it to do, but it could be much more sophisticated". No concurrent transforms over overlapping graph regions, no homomorphism-based forwarding, the transform can't be changed mid-flight. Treat it as a special-purpose tool, not a general reactive operator. See the [package index](_index.md).

## What it's for

A *transform* is an `Object` that describes a covariant change to be applied to a value **and** to everything derived from it — the canonical case being "apply the same map to this value and to each depender that would otherwise re-derive the same way," so manual edits survive a bulk change instead of being recomputed away. When a value is asked to transform, the request fans out to all transitive [`Depender`](../Depender.md)s, each of which consults its own [`TransformHandler`](TransformHandler.md) for a [`TransformReaction`](TransformReaction.md) deciding what to do (mutate in place, replace, just propagate, recompute, or ignore). See [combinations/TransformableDependency.md](../combinations/TransformableDependency.md) for the combined read+transform contract.

## Key methods by purpose

- **Configuration / lookup** — `getTransformHandler(transform)` returns the handler for a given transform object, or `null` if this value doesn't handle that kind (then `RECOMPUTE` is assumed — see below).
- **Aspect casts** — `asDependency` / `asDepender` return `this` cast to [`Dependency`](../Dependency.md)/[`Depender`](../Depender.md), or `null` if it isn't one. The fan-out only continues from values that are `Dependency`s.
- **Transform transactions** — `transformTransaction` (default) wraps `beginTransformTransaction`/`endTransformTransaction` in a [`Suppressor`](../suppress/Suppressor.md). These are **distinct from ordinary `transaction`s**: they only *mark* the object as transforming; they don't suppress recomputation by themselves. While one is active, `set(..)` and `permaInvalidate` **block** until the transform ends.
- **The driver** — `transform(transform, afterCollect)` (default): the whole protocol (collect → start transactions → run jobs → end). See *The transform protocol* below.
- **Collect phase** — `collectTransformReactions(transform, reactions, tts, releaseAfterCollect, afterTransform)` (default): recursively gathers each value's reaction into the `reactions` map.
- **Execute hooks (impl-provided)** — `runTransformRevalidate` and `runTransform(TypedReaction<E>)` actually carry out the per-value work; `valueTransformMutated` fires a [`TransformValueEvent`](TransformValueEvent.md) after an in-place mutation.
- **Guarding ongoing transforms** — `checkForTransformEnd` / `checkForTransformEnd(BehaviorDuringTransform)` decide what an operation does when a transform is in progress (no-op / block / throw), per [`BehaviorDuringTransform`](BehaviorDuringTransform.md); may throw [`TransformingException`](TransformingException.md).
- **Writes** — `set(E)` (re-declared covariant); `setNull` default delegates to `set(null)`; `applyCorrection(E)` mirrors [`CorrigibleValue.applyCorrection`](../CorrigibleValue.md).

The per-method contracts are in the javadoc; this doc only adds the protocol-level picture and the gotchas the javadoc doesn't surface.

## The transform protocol (`transform`, `TransformableValue.java`)

Three phases, all run with `ListenValue.DEFER` suppressors incremented (events are batched until the end):

1. **Collect** — under the global lock `GLOBAL_TRANSFORM_COLLECT_MUTEX`, `collectTransformReactions` walks `this` and every transitive depender once (`reactions` doubles as the visited set, ), opening a **transform transaction** on each and recording its `TransformReaction`. Propagation continues to dependers **only** for the propagating reaction types (`JUST_PROPAGATE_*`, `MUTATE`, `REPLACE`), not for `IGNORE`/`UNCHANGING`/`RECOMPUTE`. After collection, `releaseAfterCollect` is released.
2. **Start transactions + dispatch** by reaction type: `RECOMPUTE` opens a transaction and calls `runTransformRevalidate`; `MUTATE`/`REPLACE` open a transaction and queue the reaction (`r` is itself the `Runnable`) into sync or async jobs depending on `r.fast`; `JUST_PROPAGATE_WITH_TRANSACTION` opens a transaction only. The `afterCollect` callback runs here, then jobs run via `StandardExecutors.parallel` — **fast reactions sequentially in the calling thread, the rest in parallel threads**.
3. **End** — closing the regular transactions causes `RECOMPUTE` values to revalidate. The `afterTransform` runnables (queued by the `UNCHANGING` case) run last in a `finally`.

### Reaction semantics (collect side)

`collectTransformReactions` resolves each reaction:
- handler `null` → **`RECOMPUTE`** assumed; a `null` reaction from a non-null handler → **`IGNORE`**.
- **`UNCHANGING`**: snapshots validity/value now and queues an `afterTransform` job that, once the transform is over, either re-`set`s the captured value or `revalidate`s — restoring the pre-transform state. If the value is [`AutoValidationSuppressible`](../AutoValidationSuppressible.md), auto-validation is suppressed (via a *weak* suppressor) across the gap so the value doesn't auto-recompute mid-transform. **Note the deliberate fall-through:** the `UNCHANGING` case has no `break` and falls into `IGNORE`/`RECOMPUTE` so it still hands its transform transaction to `releaseAfterCollect` — this is intentional, not a missing-break bug.

## Salient / surprising behavior

- **Two transaction flavors.** A *transform transaction* (`transformTransaction`) is not a recompute-suppressing `transaction`; it is a marker that blocks `set`/`permaInvalidate`. The protocol opens *both* kinds, at different times, for different reaction types. Don't conflate them — see [concepts/transactions.md](../../../concepts/transactions.md) for the ordinary transaction model.
- **`null` handler ≠ "do nothing".** A value with no handler for the transform defaults to **`RECOMPUTE`**, i.e. it *will* be invalidated and recomputed. Only an explicit `IGNORE` (or a `null` reaction from a real handler) leaves it untouched.
- **Global serialization.** The collect phase holds one process-wide lock (`GLOBAL_TRANSFORM_COLLECT_MUTEX`), so transforms can't be collected concurrently — consistent with the "no concurrent transforms" caveat.
- **Avoid `set`/`permaInvalidate` from transform-time code**, as the javadoc warns: they block on the active transform transaction and can deadlock the transforming thread.
- **`fast` defaults to `true`** ([`TransformReaction.java`](TransformReaction.java)), so unless a reaction opts out it runs sequentially in the triggering thread.

## Override map

The default methods (`transform`, `collectTransformReactions`, `transformTransaction`, `setNull`) live entirely on the interface and orchestrate the protocol. The remaining abstract methods are implemented by [`PileImpl`](../../impl/PileImpl.md) (see its *Transform* method group): `beginTransformTransaction`/`endTransformTransaction`, `runTransform`/`runTransformRevalidate`, `checkForTransformEnd`, `getTransformHandler`/`_setTransformHandler`, and the `transformTransactions` sub-counter rolling into the open-transaction total. `set`/`applyCorrection` are the ordinary `PileImpl` write path. **For actual semantics of what a mutate/replace/recompute does to the stored value, read `PileImpl` — the interface only schedules them.**

## Common tasks (how to…)

- **Make a value transformable with custom behavior:** install a [`TransformHandler`](TransformHandler.md) on the `PileImpl` (`_setTransformHandler`) that returns a [`MutateReaction`](MutateReaction.md)/[`ReplaceReaction`](ReplaceReaction.md)/`TransformReaction` for the transform objects you care about; return `null` for ones you don't handle (→ falls back to `RECOMPUTE`).
- **Apply a transform to a value and its derivations:** call `transform(transformObject, afterCollect)` on the root value; pass an `afterCollect` runnable to do work after reactions are collected and transactions are open but before jobs finish.
- **Keep a value unchanged through a transform:** have its handler return `UNCHANGING` — the framework snapshots and restores it.
- **Guard code that must not run mid-transform:** call `checkForTransformEnd(BehaviorDuringTransform.…)` and handle/expect [`TransformingException`](TransformingException.md).

## Caveats & gotchas

- Rudimentary by design (see top): no concurrency over overlapping regions, no homomorphism forwarding, no mid-flight change of the transform.
- The default-`RECOMPUTE` rule for missing handlers is easy to overlook and means an unconfigured depender is *not* inert.
- The doubled blocking semantics around `set`/`permaInvalidate` during a transform transaction are a deadlock footgun for code that runs as part of recomputation/event handling during the transform.
- Idiomatic no-ops (not bugs): a `set`/invalidation that is silently blocked or dropped because a transform is in progress, and `BehaviorDuringTransform.NOP` doing nothing, are intended behaviors of the mechanism — see [`BehaviorDuringTransform`](BehaviorDuringTransform.md).

## Tech debt / warts

- The whole mechanism is flagged immature; expect API churn (consistent with the project-wide "names may change" caveat in [overview.md](../../../overview.md)).
- Repeated javadoc typos ("hat" for "that", "ans so on", "it a transform"); harmless but pervasive in this file.
- A commented-out `tts.release` with an explanatory note records that an earlier ordering let transforming values be observed while temporarily invalid — a known sharp edge in the design.

## Related

- Package index: [_index.md](_index.md) · Project: [overview.md](../../../overview.md) · Transactions model: [concepts/transactions.md](../../../concepts/transactions.md).
- Siblings: [`TransformHandler`](TransformHandler.md) · [`TransformReaction`](TransformReaction.md) · [`BehaviorDuringTransform`](BehaviorDuringTransform.md) · [`MutateReaction`](MutateReaction.md) · [`ReplaceReaction`](ReplaceReaction.md) · [`TransformingException`](TransformingException.md) · [`TransformValueEvent`](TransformValueEvent.md).
- Implementation: [`PileImpl`](../../impl/PileImpl.md) (*Transform* method group). · Combined contract: [combinations/TransformableDependency.md](../combinations/TransformableDependency.md).
