# `pile.aspect.transform` — package index (Tier 1)

Source folder: `src` (all types below).

The **transform mechanism**: propagating a covariant/homomorphic transformation from a value to its dependers (e.g. apply the same map to a value and the dependers that would re-derive it that way, or keep manual modifications through the transform). Per the project `README`, this feature is **rudimentary/immature** — no concurrent transforms on overlapping graph regions, the transformation can't be changed mid-flight. The live machinery is `PileImpl`'s transform methods (see [PileImpl](../../impl/PileImpl.md)); these types are the contract.

Up: [aspect index](../_index.md) · [overview](../../../overview.md) · [concepts/transactions.md](../../../concepts/transactions.md).

## The aspect
- [`TransformableValue`](TransformableValue.md) — the aspect for values that support transforms (`transform`, `runTransform*`, `checkForTransformEnd`, transform transactions); protocol on the interface, mechanics in `PileImpl`. **Gotcha: a `null` handler defaults to `RECOMPUTE`.**

## Handlers
- [`TransformHandler`](TransformHandler.md) — SAM that, given a value + transform object, decides the `TransformReaction`; plus constant handlers and the `TypedReaction` base. (`react`→`null` ⇒ `IGNORE`; absent handler ⇒ `RECOMPUTE`.)
- [`MultiTransformHandler`](MultiTransformHandler.md) — combines several `TransformHandler`s into a dispatch chain; first guarded handler returning a non-`null` reaction wins, else a configurable default.

## Reactions
- [`TransformReaction`](TransformReaction.md) — the reaction kinds (`IGNORE`/`UNCHANGING`/`MUTATE`/`REPLACE`/`RECOMPUTE`/`JUST_PROPAGATE_*`) a `TransformHandler` returns. Only `MUTATE`/`REPLACE` are executed by `PileImpl.runTransform`.
- [`MutateReaction`](MutateReaction.md) — a `MUTATE` reaction: transforms the value by mutating it in place (same reference; guarded by an identity snapshot).
- [`ReplaceReaction`](ReplaceReaction.md) — a `REPLACE` reaction: replaces the held value with a newly computed one (re-stored via `__conditionalSecretSet`).

## Control & events
- [`BehaviorDuringTransform`](BehaviorDuringTransform.md) — what a `set`/read/invalidation does while a transform is in progress (`NOP`/`BLOCK`/`THROW_TRANSFORMINGEXCEPTION`); dispatched by `PileImpl.checkForTransformEnd`.
- [`TransformingException`](TransformingException.md) — unchecked exception thrown when an operation can't proceed because a transform is in progress.
- [`TransformValueEvent`](TransformValueEvent.md) — the [`ValueEvent`](../listen/ValueEvent.md) subclass marking a transform-origin change, filterable via `ignoreTransformEvents`.
