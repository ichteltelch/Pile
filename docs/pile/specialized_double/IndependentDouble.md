# `IndependentDouble`

Always-valid, non-recomputing leaf `double` value; the typical type for persisted double settings.

Source folder: `src`. Package: `pile.specialized_double`.

Up: [_index.md](_index.md) · [overview](../../overview.md). Generic counterpart: [../impl/Independent.md](../impl/Independent.md).

## What it is

`IndependentDouble` is the `double`/`Double` specialization of `Independent<Double>`, extending `IndependentComparable<Double>` and implementing `ReadWriteListenDependencyDouble`. It is an always-valid leaf: it never recomputes, has no dependency graph parents, and accepts writes directly. It is what `PrefInterop.doublePreference` vends for persisted settings.

## Delta over the generic

The body adds only two typed overrides:
- `setName(String)` — returns `this` (typed `IndependentDouble` for fluent chaining).
- `setNull()` — calls `set(null)` and returns `this`.

No `not()`-equivalent / memoized derived value (unlike `IndependentBool.not`). No `validBuffer`/`validBuffer_memo` — those overrides are commented out in the source.

## Caveats & gotchas

- **Preference equivalence:** when created via `PrefInterop.doublePreference`, the `IndependentDouble`'s change-equivalence is `STRING_EQUIVALENCE` (two doubles are "same" if `a.toString().equals(b.toString())`). This avoids spurious preference-store writes from float round-trips but means the value's notion of "changed" is its decimal string, not its bit pattern. See [_index.md](_index.md) § Float / NaN / infinity & toString-equivalence.
- **Invalidation is silently ignored.** Calling `allowInvalidation` / `invalidate` has no effect on an `Independent` — it is always valid by design. This is idiomatic.
- Writes always succeed (no silent ignore); the value changes and listeners are notified normally.
