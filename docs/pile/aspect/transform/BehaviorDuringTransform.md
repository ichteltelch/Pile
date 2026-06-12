# `BehaviorDuringTransform`

Enum controlling what an operation (a `set`, a read/recompute, an invalidation) does when it runs while a value is mid-transform.

Source folder: `src`. Package: `pile.aspect.transform`.

See the package [_index.md](_index.md) · [overview](../../../overview.md) · siblings [TransformingException.md](TransformingException.md), [TransformableValue.md](TransformableValue.md) · impl [PileImpl.md](../../impl/PileImpl.md) · concepts in [../../../concepts/](../../../concepts/).

## The constants

Three behaviors (declaration order in `BehaviorDuringTransform.java`):

- **`NOP`** — proceed as usual; ignore the in-progress transform. This is also the early-out: `checkForTransformEnd` returns immediately for `NOP`.
- **`BLOCK`** — wait until the transform finishes, then proceed. Implemented as a `wait`-loop.
- **`THROW_TRANSFORMINGEXCEPTION`** — throw a [`TransformingException`](TransformingException.md) immediately if a transform is ongoing.

The aspect contract lives on [`TransformableValue`](TransformableValue.md): `checkForTransformEnd` (uses the per-value default) and `checkForTransformEnd(BehaviorDuringTransform)` (explicit) — `TransformableValue.java`.

## Where it's used — `PileImpl.checkForTransformEnd(bdt)`

`checkForTransformEnd(bdt2)` is the single dispatch point. For non-`NOP`, it synchronizes on the transform mutex and, while `transformThread != null || transformTransactions > 0`, either throws (`THROW_TRANSFORMINGEXCEPTION`) or waits in 1-second slices (`BLOCK`). Two notable short-circuits before the loop:

- returns immediately if the value is `destroyed`;
- returns immediately if the **current thread is the transform-transaction starter** (`transformTransactionStarterThread == Thread.currentThread`, `PileImpl.java`) — i.e. the thread driving the transform is never blocked by / never throws against its own transform.

Callers inside `PileImpl` pass a fixed `bdt`, not the per-value field:

- **recompute completion** passes `THROW_TRANSFORMINGEXCEPTION` — a recomputation that finishes mid-transform aborts rather than racing it.
- **`set(E)`** passes `BLOCK` — a write waits for the transform to end, then applies.
- **`permaInvalidate`** passes `BLOCK` — invalidation waits for the transform to end.

## The per-value default field — `setBehaviorDuringTransform`

Each `PileImpl` carries `BehaviorDuringTransform bdt = NOP`, settable via `setBehaviorDuringTransform(b)` (`PileImpl.java`; declared on [`Pile`](../../combinations/Pile.java) at line 182, returns `this` for chaining). The **no-arg** `checkForTransformEnd` reads this field and delegates. So the field is the default policy for callers that don't specify one; the three internal callers above override it explicitly. With the default `NOP`, the no-arg check is a no-op.

## Caveats & gotchas

- The field default is `NOP`, so unless you call `setBehaviorDuringTransform`, `checkForTransformEnd` does nothing — the per-value policy only bites code paths that route through the no-arg form.
- The internal `set` / `permaInvalidate` / recompute paths hard-code their `bdt` and do **not** consult the per-value field; setting the field does not change how those three behave.
- `BLOCK` waits in 1-second polling slices and re-checks; an interrupt aborts the wait and re-interrupts the thread.
- The thread starting the transform transaction is exempt (won't block/throw against its own transform).

## Tech debt / warts

- Transform support is project-flagged as rudimentary/immature (see [_index.md](_index.md)); this enum is a small, stable part of that contract.
- The javadoc on the constants is terse; the real meaning of each is only visible at the `checkForTransformEnd` call sites listed above.
