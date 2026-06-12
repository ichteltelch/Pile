# `ReadListenDependencyBool` — adds `fallback` and `whileTrueRepeat` to the read/listen/dependency bool surface

Source folder: `src`. Package: `pile.specialized_bool.combinations`.

Narrows [`ReadListenDependency`](../../aspect/combinations/ReadListenDependency.md) to `Boolean` by unioning [`ReadListenValueBool`](ReadListenValueBool.md) and [`ReadDependencyBool`](ReadDependencyBool.md), and adds two genuinely new members.

## New members

- `fallback(Boolean v)` — returns a `SealBool` that takes the constant `v` whenever `this` is invalid; writes to the returned value are redirected back to `this`. Delegates to `Piles.fallback`.
- `setName(String)` — abstract covariant override; narrows the return type to `ReadListenDependencyBool` for fluent chaining.
- `whileTrueRepeat(intervalMillis, job)` — repeatedly executes `job` on `StandardExecutors.delayed()` while this holds `true`; returns a `Pile<?>` wrapping the `ScheduledFuture`. Invalidate/suppress-autovalidation to pause; destroy to stop forever.
- `whileTrueRepeat(intervalMillis, mayInterrupt, scheduler, job)` — full form; delegates to `PileBool.whileTrueRepeat`.

## Caveats

The `Pile<?>` returned by `whileTrueRepeat` is GC-sensitive: losing all references to it silently ends the repeat loop. Retain it (or keep the hosting reactive graph alive) for as long as the behavior is needed.

Up: [combinations index](_index.md) · [bool package index](../_index.md) · [overview](../../../overview.md).
