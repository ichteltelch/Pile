# `LimitedResource`

A counted, capacity-limited resource that competing requesters (*supplicants*) wait on and consume — currently a **skeleton** with the counter state but no acquire/release logic yet.

Source folder: `src` · package `pile.aspect.limitedresource`.

Up: [package index](_index.md) · [overview](../../../overview.md). Sibling: [`Supplicant`](Supplicant.md).

## What it models

A named resource with a fixed total capacity `max` and a running `used` count. The intent (per the package's framing) is that several `Supplicant`s request units of the resource, wait until capacity is free, hold their share, and release it. The class is **peripheral** to Pile's core reactive machinery and stands largely on its own.

## State

- `name` — public final identifier for the resource.
- `max` — total capacity (private, set in the constructor; no setter, no getter).
- `used` — units currently consumed; initialised to `0`.
- `available` — a public `ObservableCondition` whose underlying `GuardedCondition` (in `pile.interop.wait`, `src`) guards on the predicate `used < max`. A waiter blocking on `available` is released only when that predicate holds (the `GuardedCondition` loops re-checking it; see `GuardedCondition.await`), and signalling is gated on it too. The `ObservableCondition` wrapper additionally lets observers be notified on each signal.
- `LR_LOCK` — a single **static, fair** `ReentrantLock` shared by *all* `LimitedResource` instances; `available`'s condition is derived from it. So the lock is process-wide, not per-resource.

## Salient / surprising behavior

- **No acquisition or release API exists yet.** There is no method to take, free, or query the resource; `used` is never mutated after construction (`max`/`used` are private with no mutators). As written, `available`'s predicate `used < max` is effectively constant for the lifetime of the object. The class is an embryonic stub.
- **The arbitration lock is `static`.** All resources serialise through one global fair lock. Whether that is intended (global arbitration) or a placeholder is unclear, but it means contention on one `LimitedResource` blocks operations on unrelated ones once the acquire/release logic is filled in.
- **`Supplicant` already defines the arbitration order** it expects — see [`Supplicant`](Supplicant.md): supplicants are ordered by `priority`, then `deadline`, then instance id, with a separate `BY_DEADLINE` comparator. That ordering machinery exists but nothing here consumes it yet.

## Caveats & gotchas

- Treat this as **unfinished**. Don't rely on it for real resource limiting; there is no way to consume or release capacity through the public surface.
- `max` is mutable internally (non-final) but has no setter — likely anticipating a resize feature that isn't written.
- The shared static lock means any future per-resource semantics must account for global serialisation.

## Common tasks

- *Construct one:* `new LimitedResource(name, max)` — sets the name and capacity, `used = 0`.
- *Wait for availability (intended):* block on the `available` condition via the project's `WaitService`; it wakes when `used < max`. Note that, until release logic exists, nothing will ever signal a change.

## Tech debt / warts

- Core lifecycle (acquire / release / try-acquire / current-usage query) is missing.
- Global `static` arbitration lock — questionable for independent resources; revisit when wiring up the real logic.
- `Supplicant` and `LimitedResource` are present but not connected: no field on either references the other, and `LimitedResource` has no references anywhere in the codebase.

## Related

- [`Supplicant`](Supplicant.md) — the requester abstraction with the priority/deadline ordering.
- Waiting primitives `ObservableCondition` / `GuardedCondition` live under `pile.interop.wait` (`src`).
