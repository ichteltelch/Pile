# `pile.interop.preferences.PreferencesBackedValue`

A reactive, writable, listenable value that mirrors a single `java.util.prefs.Preferences` entry in real time — yet is **always-valid and deliberately *not* a `Dependency`**.

Source folder: `src`. File: `pile/interop/preferences/PreferencesBackedValue.java`.

`PreferencesBackedValue<T>` implements [`ReadWriteListenValue<T>`](../../aspect/combinations/ReadWriteListenValue.md), `ListenValue.Managed`, and [`AlwaysValid<T>`](../../aspect/AlwaysValid.md). It is the bidirectional bridge between a Pile-style reactive value and one key in a `Preferences` node: writes go through to the node, and external changes to that node (from anywhere in the JVM) flow back into the value and fire listeners. Compare its sibling [`SynchronizingFilesBackedValue`](SynchronizingFilesBackedValue.md) (same idea, file-backed) and the factory hub [`PrefInterop`](PrefInterop.md). See the [package index](_index.md) and the project [overview](../../../overview.md).

## What it wraps

Constructed from four things (`PreferencesBackedValue(Preferences, String, Bijection, Supplier)`):

- `node` + `key` — the exact `Preferences` slot this value mirrors. `dependencyName` / `toString` derive from these (`node.absolutePath() + "/" + key`).
- `codec` — a `Bijection<T,String>`; `encode` is the codec, `decode = codec.inverse()`. Every value is round-tripped through this to/from the string actually stored in the node.
- `defaultValue` — a `Supplier<? extends T>` consulted whenever the key is absent (or a stored string fails to decode).

Cached state: `currentValue` (the live `T`) and `currentString` (its encoded form, used to short-circuit redundant re-decodes in `_read`).

## Read/write through to the node

- **`get`** returns the cached `currentValue` — no node access on the read path (the cache is kept current by the change listener).
- **`set` / `accept`** call `write(value, false)`, which (in `_write`) skips if the new value is `equivalence`-equal to the current one, else updates the cache, `encode`s, and calls `node.put(key, …)`. `notifyAll` wakes any thread blocked in `await`. A real change fires listeners via `fireValueChange`.
- **`read`** (private) calls `_read`: it pulls `node.get(key, null)`; a `null` (absent key) installs `defaultValue.get()`; otherwise it skips if the raw string is unchanged, else `decode`s it. A decode failure is **logged and falls back to the default** rather than propagating (see `_read`, the `catch` branch). Returns whether the value actually changed (by `equivalence`), gating the listener fire.
- **`reset`** calls `node.remove(key)`. It does **not** itself update the cache or fire — it relies on the registered `PreferenceChangeListener` to observe the removal and re-`read` (which reinstalls the default). See *Caveats*.
- **`valueMutated`** does `write(currentValue, true)` — a forced write/re-encode of the current value, for when `T` was mutated in place.

## Change observation (the round trip)

The constructor registers a `PreferenceChangeListener` on `node`. On any `PreferenceChangeEvent` whose key matches, it calls `read()`, so **external edits to the preference — including via `reset` and edits from other code/processes — propagate back into this value and fire listeners.** This is the mechanism that keeps `get` cheap and the cache authoritative.

The listener is held alive only as long as this value is, via `WeakCleanupWithRunnable`: the listener captures a weak handle (`belong`) to the `PreferencesBackedValue`; when that handle is cleared (value garbage-collected) the listener removes itself from the node on its next firing, and the registered cleanup action also removes it. So you need not unregister manually — dropping all references is enough.

Listener plumbing is the standard `ListenValue.Managed` pattern: `_getListenerManager` lazily double-checked-creates a `ListenValue.ListenerManager`, and `fireValueChange` forwards to it (no-op until someone has subscribed, since `manager` stays `null`). Add listeners through the usual `ListenValue` methods.

## The always-valid / not-a-dependency nature

This is the surprising part, and it is shared with `SynchronizingFilesBackedValue` — see [`AlwaysValid`](../../aspect/AlwaysValid.md), which calls these two out explicitly. Although the value is writable and listenable, it mixes in `AlwaysValid<T>`, asserting it is **never invalid**: the backing `Preferences` node is treated as always current, so all of `ReadValue`'s blocking/validity surface collapses to `get` (no waiting, no invalidation, no "old value"). Consistent with that, the lifecycle/transaction surface is inert:

- `isValid`/validity come from `AlwaysValid`; `willNeverChange` is `false`; `isDestroyed` is `false`.
- `isInTransaction` is `false`, `inTransactionValue` is the constant `Piles.FALSE`; `__beginTransaction`, `__endTransaction`, `revalidate`, `permaInvalidate` are **no-ops** — this value does not do transactions and cannot be invalidated.
- `remembersLastValue` is `false`; `storeLastValueNow` / `resetToLastValue` are no-ops; `suppressRememberLastValue` returns `Suppressor.NOP`. (The persistence *is* the preference node — there is no separate last-value memory.)
- `applyCorrection(v)` returns `decode(encode(v))` — i.e. corrections are exactly the codec round-trip, normalising a value to what the store would yield.

Crucially it **does not implement [`Dependency`](../../aspect/Dependency.md)**, so you cannot make a `Pile` depend on it directly. To depend on it, call **`asDependency()`** (a convenience that redirects to `ReadWriteListenValue.writableValidBuffer_memo()`), which gives you a memoised writable valid buffer that *is* a dependency target.

## Blocking waits

`await(WaitService, BooleanSupplier)` and the timed overload poll the condition, `wait`-ing on `this` (with a 1000 ms cap per wait) and relying on the `notifyAll` calls in `_write`/`_read` to wake. These are the `ReadValue`-style "wait until condition" helpers; because the value is always valid they are rarely needed, but they observe live updates.

## Equivalence

`_setEquivalence` / `_getEquivalence` expose the `BiPredicate` used to decide whether a write or re-read is a real change. Default is `ReadWriteDependency.DEFAULT_BIJECT_EQUIVALENCE`. Note `_read`'s absent-key branch always returns `true` (treats reinstalling the default as a change) regardless of equivalence.

## Caveats & gotchas

- **`reset` is asynchronous and listener-dependent.** It only calls `node.remove(key)`; the cache update and listener fire happen later, when the registered `PreferenceChangeListener` observes the removal and re-`read`s. If preference change events are not delivered, the cache will go stale. (Contrast `set`, which updates synchronously.)
- **Not a `Dependency`.** Depending on it requires `asDependency()` / `writableValidBuffer_memo()`; passing the bare value where a dependency is expected won't compile/work. This is by design (the `AlwaysValid` doc explains why).
- **Always-valid is a promise.** Mixing in `AlwaysValid` asserts the value is never invalid. A missing/undecodable key silently becomes the default (logged at `WARNING`), so consumers never see invalidity — they just see the fallback.
- **Decode failures are swallowed** into the default value (`_read`). A persistently malformed stored string yields the default on every read; the bad string is logged, not surfaced.
- **No transactions / no last-value.** All transaction hooks and remember-last-value operations are no-ops; don't expect batching or rollback semantics here.
- **Listener self-cleanup is GC-timed.** Unregistration from the node happens when the weak handle clears and the listener next fires (or via the cleanup action); there is no explicit `dispose`.

## Common tasks (how to…)

- **Mirror a single preference as a reactive value:** construct one (usually via [`PrefInterop`](PrefInterop.md) rather than directly), supplying node, key, a `Bijection<T,String>` codec, and a default `Supplier`.
- **Depend on it in a Pile graph:** call `asDependency()` and use the returned `Independent` / writable valid buffer as the dependency.
- **React to external preference edits:** add a `ValueListener` via the `ListenValue` API; the registered `PreferenceChangeListener` will drive `read` → `fireValueChange` when the node changes.
- **Clear the preference back to its default:** call `reset()` (remember it propagates via the change listener, not synchronously).
- **Force a re-store after in-place mutation of `T`:** call `valueMutated()`.

## Tech debt / warts

- Several no-doc `@Override` methods (lifecycle/last-value) exist only to satisfy the rich `ReadWriteListenValue` contract; their no-op nature is a usage gotcha, not a bug (per the [guide's idiomatic-silence note](../../../overview.md)).
- The class is undocumented at the member level — the class Javadoc only notes "does not implement `Dependency`"; behavior must be read from the source.
- The `reset`-via-listener indirection makes `reset` semantically different from `set` (async vs sync), which is easy to trip over.

## Related

- [`AlwaysValid`](../../aspect/AlwaysValid.md) — the mixin; documents this class as a surprising consumer.
- [`SynchronizingFilesBackedValue`](SynchronizingFilesBackedValue.md) — the file-backed sibling with the same always-valid/not-a-dependency design.
- [`PrefInterop`](PrefInterop.md) — factories that build these and related preference-backed values.
- [`ReadWriteListenValue`](../../aspect/combinations/ReadWriteListenValue.md) — the read/write/listen contract, source of `asDependency` / `writableValidBuffer_memo`.
- [`Dependency`](../../aspect/Dependency.md) — the aspect this value deliberately omits.
- [package index](_index.md) · [overview](../../../overview.md) · [concepts](../../../concepts/)
