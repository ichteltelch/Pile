# `pile.builder.ISealPileBuilder`

The fluent builder interface for [`SealPile`](../impl/SealPile.md)s (a `Pile` that is also sealable) — and the home of the `setup*` buffer / deref / field / rate-limited / delayed / defaultable wirings that the [`Piles`](../impl/Piles/_index.md) factories delegate to.

Source folder: `src`. File: `pile/builder/ISealPileBuilder.java`.

`ISealPileBuilder<Self, V extends SealPile<E>, E>` is a *target* interface in the layered builder hierarchy (see the [builder index](_index.md)). It is the `SealPile` analogue of [`IPileBuilder`](IPileBuilder.md): you normally obtain a concrete builder from a `Piles` factory and finish with `build`.

## What it combines
`ISealPileBuilder` extends **both** capability lines and adds the buffer machinery:
- [`IPileBuilder`](IPileBuilder.md) — the full `Pile` fluent surface: `recompute*`, `dependOn`, `dynamicDependencies`, `delay`/`pool`, `equivalence`, brackets, `init`, `parent`, etc. (Itself inherits `ICorrigibleBuilder` + `IListenValueBuilder` + the root `IBuilder`.)
- [`ISealableBuilder`](ISealableBuilder.md) — the **seal** surface: `seal`, `seal(interceptor)`, `seal(interceptor, allowInvalidation)`, the `Function`/`BiConsumer`/`sealWithSetter` convenience overloads, `giveSetter`, `makeSetter`.

So `ISealPileBuilder` = "a `Pile` builder that can also seal", and on top of that it adds the `setup*` defaults below. Nothing on this interface is abstract — every method here is a `default`. The abstract `build` wiring (including applying the recorded seal last) lives in [`AbstractSealPileBuilder`](AbstractSealPileBuilder.md) and `AbstractPileBuilder` *(doc pending)*.

### How sealing is deferred (important for reading the `setup*` code)
`seal(...)` does **not** seal immediately — `AbstractSealPileBuilder` records `sealOnBuild`/`interceptor`/`allowInvalidation` and applies `value.seal(interceptor, allowInvalidation)` at the **end of `build`**. That is why the `setup*` methods can call `seal(...)` early in their fluent chain and still keep configuring afterwards. The seal flavours:
- `seal` → read-only sealed value (writes rejected).
- `seal(interceptor)` → writes redirected to `interceptor`; `permaInvalidate` **forbidden** (`allowInvalidation=false`, `ISealableBuilder.java`).
- `seal(interceptor, allowInvalidation)` → as above but `allowInvalidation` controls whether `permaInvalidate` is permitted. The buffer setups use `seal(setter, false)`.

## The `setup*` family — what each wires up
These are the **implementation behind the `Piles` buffer/deref family** (the `Piles` index flags which factory routes to which). They act **eagerly** on `valueBeingBuilt` (auto-name it via `avName`, attach listeners/correctors, set equivalence) and return the builder with the seal already recorded — so the caller only needs `.build`. The common pattern across the buffers: a `ValueListener` on the leader pushes the leader's value/validity into the follower through a **`WeakCleanupWithRunnable`-wrapped setter**, so the *follower holds no strong reference to itself from the leader* — when the follower is GC'd, the cleanup action unregisters the listeners.

### Deref / field (extract a reactive value from a reactive value)
Built as recompute-driven sealed `Pile`s with **dynamic dependencies** + `essential(derefThis)` + `parent(derefThis)`:
- `setupDeref(ReadDependency<? extends C>)` — dereference a `ReadDependency`-valued `ReadDependency`; delegates to `setupField(derefThis, false, id)`. Backs `Piles.deref`.
- `setupField(derefThis, nullable, extract)` — follower takes the value of the inner `ReadDependency` produced by `extract`; if the extracted value is `null`/invalid the follower is invalid. `nullable` decides whether `extract` is called on a `null` outer value. Backs `Piles` deref/field.
- `setupField_lowExtract(derefThis, nullable, extract, equiv)` — same contract but **caches** the last extracted inner value keyed on the outer value's identity (`lastC`/`lastF`) and only re-runs `extract` when `derefThis` actually changed (uses a `GenericDependencyRecorder` under `Recomputations.withDependencyRecorder` to record the dependencies `extract` itself reads, then replays them). Trades overhead for avoiding `extract` calls. The optional `equiv` sets the follower's equivalence.
- `setupWritableDeref(...)` / `setupWritableField(...)` / `setupWritableField_lowExtract(...)` — the writable twins: the recomputer remembers the extracted inner value in a `current` field, and the seal interceptor forwards writes to `current.set(v)` (silently no-ops if `current==null`). `seal(setter)` here is the no-`boolean` form, so `permaInvalidate` is forbidden on these.

### Buffers (decouple / shorten transaction cascades)
A buffer is a follower that mirrors a leader but, per the javadoc, **does not enter the leader's transaction** — its purpose is to "shorten transaction cascades". It inherits the leader's brackets (`inheritBrackets(false, leader)`) and equivalence, and parks a strong ref to the leader in the follower's `owner` field *unless that field is already set*.
- `setupBuffer(ReadListenValue<E>)` — read-only buffer; `seal`. Backs `Piles.buffer`.
- `setupWritableBuffer(ReadWriteListenValue<E>)` — writable buffer; also installs a corrector from the leader; the seal interceptor does `followerSetter.set(leader.set(newValue))` — **writes hit the leader first, then the follower**. Backs `Piles.writableBuffer`.
- `setupWeakBuffer(...)` / `setupWritableWeakBuffer(...)` — same, but the link **to the leader is weakly referenced** (the `owner` holds the `WeakReference`, the listener and corrector all `.get` and bail if cleared). Backs `Piles.weakBuffer`/`writableWeakBuffer`. Note the writes set propagation uses `setter.set(...)` whereas the read-only buffer uses `setter.accept(...)` — see warts.
- All buffers push the leader's value under `Piles.withShouldDeepRevalidate(false)` (so a buffer update does not trigger deep-revalidation), and `permaInvalidate` the setter when the leader is invalid. They call `cl.runImmediately(true)` once to seed the initial value.

### Rate-limited buffers
`setupRateLimited(leader, coldStartTime, coolDownTime)` and `setupWritableRateLimited(...)` — like the buffers but the leader listener is a `ValueListener.rateLimited(coldStart, coolDown, …)`, throttling how often the follower follows. They additionally call `follower.transferFrom(leader, true)` up front to copy the leader's current state. The writable variant forwards writes leader-first like `setupWritableBuffer`. Back `Piles.rateLimited`/`writableRateLimited`. **Known limitation (javadoc, ): directly invalidating the buffer "does not work yet".**

### Delayed followers
- `setupDelayed(delay, leader)` — follower follows the leader through a `recompute(leader).dependOn(true, leader).delay(delay)` chain and is **invalid during the delay**. Writing the follower bypasses the delay: the seal interceptor sets a `ThreadLocal` delay-switch off, sets the leader (so the follower immediately takes the new value without announcing long-term invalidity), then restores the switch. Uses `setDelaySwitch( -> doDelay is FALSE)` to decide per-thread whether to delay. Copies the leader's equivalence and `bequeathBrackets`. (See the commented-out `main` at the bottom of the file for a runnable demo.)
- `setupDelayedRO(delay, leader)` — read-only version: same delayed-follow but `seal` with no interceptor, so the follower cannot be set explicitly.

### Defaultable
`setupDefaultable(back, defaultValue)` — follower mirrors `back` but substitutes `defaultValue` when `back.get` is `null`. The seal interceptor maps a write of `defaultValue` back to `back.set(null)`, otherwise `back.set(v)`. Backs the `Piles` "fallback/defaultable" style.

## Salient / surprising behavior
- **Eager, not buffered.** Unlike most `IPileBuilder` config that you can reorder freely, the `setup*` methods build the whole recompute/listener graph *immediately* when called and record the seal. Treat each as a terminal "make this into a buffer/deref" step — call it once, then just `build`.
- **Auto-naming via `avName`.** Each `setup*` sets the follower's debug `avName` (e.g. `"buffered (leaderName)"`, `"rate limited (...)"`, `"(x)->?"`) *only if it is still `null`* — your own `name(...)` wins if set first.
- **No self-reference from the leader.** Buffers deliberately register the listener against a `WeakCleanupWithRunnable`-wrapped setter so the follower can be collected; the cleanup unregisters the listeners. The strong leader→follower anchor is the `owner` field (or a `WeakReference` to the leader for the weak variants).
- **Deep-revalidate suppressed on buffer pushes** (`Piles.withShouldDeepRevalidate(false)`) — a buffer mirroring its leader is not meant to re-trigger the deep-revalidate cascade.
- **`_low­Extract` caches `extract`** keyed on outer-value identity (`==`), replaying recorded dependencies so the dynamic-dependency graph stays correct without re-running `extract`.

## Caveats & gotchas
- `setupRateLimited` / `setupWritableRateLimited`: invalidating the buffer directly is documented as not working yet.
- `setupField`/`setupWritableField` swallow the `extract`-returned value being `null` *or* `validity.isFalse` as "follower invalid" — there is no error path; a bad `extract` just yields an invalid follower.
- Writable deref/field write-forwarding **silently no-ops** when there is currently no extracted inner value (`current==null`) — a write can be lost without feedback (, the inline `//TODO` notes consistency is not checked).
- The buffer `owner`/`bequeathBrackets`/`transferFrom`/`applyCorrection` collaborators live on `AbstractReadListenDependency`/`SealPile` *(see [`SealPile`](../impl/SealPile.md))*; this interface only wires them.
- `init(...)` (from `IPileBuilder`) used inside `setupDelayed*` seeds the leader's current value if valid; combined with `IPileBuilder`'s `init` gotcha (brackets/correctors added later do not affect that seed), order matters.

## Common tasks (how to…)
- **A cheap buffered copy that won't drag the leader into a transaction:** `Piles.buffer(leader)` → routes to `setupBuffer`. Writable: `Piles.writableBuffer(leader)` → `setupWritableBuffer`.
- **Mirror but throttle update rate:** `Piles.rateLimited(leader, cold, cool)` → `setupRateLimited`.
- **Value-of-a-value (deref):** `Piles.deref(outer)` → `setupDeref`/`setupField`. Forward writes too: `Piles.writableDeref(outer)` → `setupWritableDeref`.
- **Extract a sub-value reactively, avoiding repeated `extract` calls:** use the `_lowExtract` field variants.
- **A value that lags its leader by a delay:** `setupDelayed`/`setupDelayedRO`.
- **A value with a default when its backing is null:** `setupDefaultable(back, def)`.
- **Memory-friendly buffer that lets the leader be collected:** the `weakBuffer`/`writableWeakBuffer` variants.

## Tech debt / warts
- `setupBuffer` pushes via `setter.accept(value)` while `setupWritableBuffer` pushes via `setter.set(value)` (and likewise the weak twins): the read-only path uses `accept`, the writable path `set`. Subtle inconsistency — worth confirming both reach the intended setter semantics.
- `setupRateLimited`'s "invalidation doesn't work yet" `TODO` is unresolved.
- Writable deref/field's lost-write-when-`current==null` and the `//TODO optionally … check for consistency` are acknowledged gaps.
- Six near-identical buffer bodies (read/write × strong/weak × rate-limited) duplicate the listener/weak-cleanup boilerplate — ripe for extraction.
- A large commented-out `main` demo is left in the source.

## Related
- [builder index](_index.md) · [overview](../../overview.md) · [`IPileBuilder`](IPileBuilder.md) (the `Pile` half) · [`ISealableBuilder`](ISealableBuilder.md) (the seal half) · [`AbstractSealPileBuilder`](AbstractSealPileBuilder.md) (build/seal-deferral wiring) · [`SealPile`](../impl/SealPile.md) (the built type) · [`Piles`](../impl/Piles/_index.md) (the factories that delegate here) · transaction model in [concepts/](../../concepts/).
