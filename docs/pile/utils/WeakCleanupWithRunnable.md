# `WeakCleanupWithRunnable`

A `WeakCleanup` whose cleanup `Runnable` is a mutable *field* on the reference itself — so a listener can hold the reference (and through it the action), stay alive exactly as long as its referent, null-check liveness via `get()`, and self-unregister when the referent is collected.

Source folder: `src`. Package `pile.utils`.

Up: [utils index](_index.md) · [overview](../../overview.md). Siblings: [`WeakCleanup`](WeakCleanup.md), [`WeakIdentityCleanup`](WeakIdentityCleanup.md).

## What it's for

`WeakCleanup` is abstract: it makes you subclass and implement `run()`. `WeakCleanupWithRunnable` is the concrete, ready-made variant — you pass the cleanup `Runnable` in (or set it later) instead of subclassing. Its `run()` simply delegates to the stored `handle`.

The decisive feature is that the action lives *on the reference object*, not in a separate registry. That lets a third party (typically a listener) keep a strong reference to the `WeakCleanupWithRunnable` and reach the action through it, which is what makes the self-unregistration pattern below work.

## How it differs from `WeakCleanup`

| | `WeakCleanup` | `WeakCleanupWithRunnable` |
|---|---|---|
| Shape | `abstract`, implements `Runnable` | concrete subclass |
| Where the action lives | you override `run()` (or use the static `runIfWeak`, which parks a private `Bucket` in a global `buckets` list) | a mutable `handle` field on the instance |
| Action mutable after construction? | no | yes — `setCleanupAction` |
| Caller holds the reference? | typically not (with `runIfWeak`, the framework's `buckets` list does) | **yes** — that's the whole point |

With `WeakCleanup.runIfWeak`, the cleanup closure is owned by the static `buckets` list, so the caller never sees the reference object and cannot ask "is my referent still alive?". `WeakCleanupWithRunnable` hands the reference object back to the caller, exposing the inherited `WeakReference.get()` as a liveness probe.

## Key members

- **`WeakCleanupWithRunnable(referent, handle)`** / **`(referent, handle, rm)`** — construct over a referent, with the cleanup action and optionally an explicit `AbstractReferenceManager`. `handle` may be `null` at construction and filled in later (see the recipe).
- **`run()`** — overrides `WeakCleanup.run()`; just calls `handle.run()`. Invoked on the manager's daemon thread once the referent is enqueued.
- **`setCleanupAction(newHandle)`** — replace the action. Used to install the unregister closure *after* the listener it must reference has been created (breaking the construction-order cycle).
- **`get()`** — inherited from `WeakReference`. Returns the referent or `null` once collected; the listener pattern uses it as a liveness gate.

## The self-unregistration pattern (`pollfRef`-style)

This is the idiom every caller in `pile.interop.preferences` follows (`PrefInterop.boolPreference`, `PreferencesBackedValue`'s constructor, `SynchronizingFilesBackedValue`):

1. Create the reactive value `ret`.
2. `WeakCleanupWithRunnable<V> weakRet = new WeakCleanupWithRunnable<>(ret, null);` — action still `null`.
3. Build a listener that captures **`weakRet`, never `ret` directly**. At the top of the callback it does `V strong = weakRet.get();` and, if `strong == null`, returns early and/or unregisters itself.
4. Register the listener with the external source (a `Preferences` node).
5. `weakRet.setCleanupAction(() -> source.removeListener(listener));`

The reference graph this builds is deliberate: the external source strongly holds the *listener*; the listener strongly holds *`weakRet`*; `weakRet`'s `handle` strongly holds the *unregister closure*, which (in turn) holds the *listener*. The only path to `ret` is the **weak** referent slot. So:

- The listener (and its cleanup action) stay reachable for **exactly** as long as the external source keeps them — i.e. as long as `ret` is meant to mirror that source.
- Once `ret` becomes unreachable elsewhere, the weak slot clears, the reference is enqueued, the manager's daemon runs `handle`, and the listener is removed from the source. No leak; the listener does not pin `ret`.

There are two redundant safety nets for "referent already gone": the GC-driven `run()` removes the listener, and the listener's own `weakRet.get() == null` branch removes it on the next event in case it fires in the race window before cleanup runs (`PreferencesBackedValue`'s `preferenceChange` does both — bails *and* removes).

## Salient / surprising behavior

- **The cycle is intentional, not a leak.** `weakRet → handle → closure → listener → weakRet` is a strong cycle, but it is rooted only off the external source's listener list, so it dies with the registration. Do not "fix" it by weakening a link.
- **`get()` is the liveness API.** `WeakCleanupWithRunnable` adds no method named `get`; it reuses `WeakReference.get()`. Callers must null-check it — a non-null result is only valid for that instant (the referent can be collected immediately after).
- **`null` handle until set.** Constructing with `null` and forgetting `setCleanupAction` means `run()` will NPE when the referent is collected. The two-step construct-then-set dance exists solely to let the closure reference a listener that doesn't exist yet at construction time.
- **Cleanup runs on a daemon thread** owned by the `AbstractReferenceManager` (the standard shared one unless an `rm` is passed). Don't assume it runs on the thread that created the reference.

## Caveats & gotchas

- Capture **`weakRet`**, not the referent, inside the listener — capturing the referent strongly defeats the entire mechanism (the listener would pin the value forever).
- Liveness from `get()` is inherently racy; treat a non-null return as "alive right now," guard the work, and tolerate the referent vanishing.
- Construction order matters: register the listener and call `setCleanupAction` together, so the action can unregister precisely that listener.

## Common tasks

- **Keep an external listener alive only while a Pile lives, auto-removing it on GC** → follow the five-step pattern above; see `PrefInterop.boolPreference` for the canonical form and `PreferencesBackedValue`'s constructor for the inline-anonymous-listener variant.
- **Swap the cleanup action later** (e.g. action depends on an object built after the reference) → `setCleanupAction`.
- **Just run a closure on GC, no liveness probe, no caller-held reference** → prefer the static `WeakCleanup.runIfWeak` instead; it's lighter and doesn't hand you a reference object.

## Tech debt / warts

- The `null`-handle-then-`setCleanupAction` two-phase init is a footgun: an instance is briefly in a state where GC would NPE in `run()`. It's safe only because every caller sets the action synchronously before the referent can plausibly be collected, but nothing enforces it.
- `handle` has package-private (default) visibility rather than `private`, so it's mutable from within `pile.utils` without going through `setCleanupAction`.

## Related

- [`PreferencesBackedValue`](../interop/preferences/PreferencesBackedValue.md) — primary user; constructor wires an anonymous `PreferenceChangeListener` to a `WeakCleanupWithRunnable`.
- `PrefInterop` (`pile.interop.preferences`) — `boolPreference` / `doublePreference` etc. all use this idiom.
- [`WeakCleanup`](WeakCleanup.md) — the abstract base and the `runIfWeak` alternative.
