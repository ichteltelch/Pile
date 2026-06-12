# `pile.builder.AbstractSealPileBuilder`

Abstract base for [`SealPile`](../impl/SealPile.md) builders: extends the `Pile`-builder base with deferred sealing — record a `seal(...)` request now, apply it on `build`.

Source folder: `src`. File: `pile/builder/AbstractSealPileBuilder.java`.

`AbstractSealPileBuilder<Self, V extends `[`SealPile<E>`](../impl/SealPile.md)`, E> extends `[`AbstractPileBuilder<Self, V, E>`](AbstractPileBuilder.md)` implements `[`ISealPileBuilder<Self, V, E>`](ISealPileBuilder.md). It is thin: it adds the seal-on-build machinery on top of the `Pile` build wiring and nothing else. Like its base, the `Self` recursive type parameter is what forces the class to be `abstract` (the concrete leaf is `SealPileBuilder`).

For *what sealing means* — the four seal modes, redirection, the `makeSetter`-before-seal pattern — read the aspect doc [`Sealable`](../aspect/Sealable.md); for how the seal is *enforced* at runtime, read [`SealPile`](../impl/SealPile.md). This doc only covers the **builder delta**.

## What it adds over the `Pile`-builder base

Three fields capture a pending seal request:

- `boolean sealOnBuild` — whether `build` should seal the value.
- `boolean allowInvalidation` — passed to `SealPile.seal(...)`; whether `permaInvalidate` stays permitted on the sealed value.
- `Consumer<? super E> interceptor` — the seal mode (the consumer that handles ordinary `set`s after sealing); `null` ⇒ default throw-on-set.

### The `seal(...)` family — just records intent

The three [`ISealableBuilder`](_index.md) `seal` overloads each set the fields and return `self`; **none of them seal immediately** — the value is only sealed in `build`:

- `seal` — sets `sealOnBuild=true` only. Leaves `interceptor=null` and `allowInvalidation=false` ⇒ default throw-mode seal.
- `seal(interceptor)` — sets the interceptor, `sealOnBuild=true`, and forces `allowInvalidation=false`.
- `seal(interceptor, allowInvalidation)` — sets all three.

### `build` — seal *after* the base wiring

`build` calls `super.build` (the full `AbstractPileBuilder` wiring) first, then, iff `sealOnBuild`, calls `value.seal(interceptor, allowInvalidation)` and returns the value from `super.build`. So sealing is the **last** step of construction — everything (recomputer, dependencies, correctors, brackets, initial value) is configured on the still-mutable value, then frozen. This is the build-then-seal order that `SealPile`'s runtime guards assume.

### `makeSetter` — pass-through to the value

`makeSetter` just returns `value.makeSetter`. The builder is the natural place to grab the privileged write channel **before** `build` seals the value: call `b.makeSetter` (or `value.makeSetter`) and retain it, then `b.seal.build`. After the seal, the value refuses further `makeSetter` calls (see [`SealPile` § caveats](../impl/SealPile.md)). Note it returns whatever `SealPile.makeSetter` returns — a `WriteValue<E>` narrowing a richer `PrivilegedWriteDepender`.

## The `setup*` buffer helpers live on the *interface*, not here

The task hint asks whether this class hosts the buffer `setup*` methods. **It does not.** `setupBuffer` / `setupWritableBuffer` / `setupWeakBuffer` / `setupRateLimited` / `setupDelayed` / `setupDeref` / `setupField` / `setupDefaultable` etc. are all `default` methods on the interface [`ISealPileBuilder`](ISealPileBuilder.md) (`ISealPileBuilder.java`, ~32 symbols). The abstract class inherits them unchanged and adds none of its own. So when looking for the buffer-wiring logic, go to the interface doc, not this one.

## Constructor

`AbstractSealPileBuilder(V value)` chains to `super(value)` and then throws `IllegalArgumentException` if `value.isSealed` — **a builder may not be created around an already-sealed value** (you could configure nothing on it). The value you hand the builder must be a fresh, unsealed `SealPile`.

## Salient / surprising behavior

- **Sealing is deferred, not immediate.** `seal` returns the builder unsealed; the value is sealed only by `build`. Calling builder methods after `seal` but before `build` still mutates a live, unsealed value — order of `seal` among other fluent calls does not matter, only that `build` runs last.
- **`seal(interceptor)` forces `allowInvalidation=false`**, unlike the two-arg form. To keep invalidation while supplying an interceptor you must use `seal(interceptor, true)`.
- **Re-calling `seal*` overwrites the prior request** — the fields are plain assignments, so the last `seal(...)` call before `build` wins (e.g. a later `seal` resets `interceptor` to null only if it were re-cleared — but `seal` does **not** clear `interceptor`; see warts).

## Caveats & gotchas

- **Grab the setter before `build`/`seal`.** Once `build` seals the value there is no way to obtain a write channel; use `makeSetter` on the builder (or the value) beforehand and retain it. (See [`Sealable` § caveats](../aspect/Sealable.md).)
- **Don't reuse a builder across `build`s** when sealing: a second `build` would call `value.seal(...)` again on an already-sealed value — re-sealing with the *same* interceptor is a silent no-op but with a *different* interceptor throws (`SealPile` seal contract). Builders are single-shot here in practice.
- **The value must be unsealed at construction** (constructor throws otherwise, ).

## Tech debt / warts

- **`seal` (no-arg) does not reset `interceptor`/`allowInvalidation`** — it only flips `sealOnBuild`. If a caller does `seal(myInterceptor, true)` then later `seal`, the no-arg call does **not** revert to default throw-mode: the previously-set `interceptor` and `allowInvalidation` survive. The one-arg and two-arg forms set all relevant fields, but the no-arg form leaves stale state. Likely harmless given single-shot fluent usage, but it is an asymmetry: `seal` is not equivalent to "seal with defaults" if a prior `seal(...)` ran. (Reported below.)
- **Stray trailing blank lines** at the end of the class body.
- Empty `@param` tags (`<V>`, `<E>`) in the class javadoc, consistent with the [project-wide note on unsystematic API](../../overview.md).

## Related

- [`AbstractPileBuilder`](AbstractPileBuilder.md) — the base providing all `Pile` build wiring (dynamic-dependency recording, threaded/delayed recompute); `build` here delegates to its `build` first.
- [`ISealPileBuilder`](ISealPileBuilder.md) — the interface this implements; hosts the `setup*` buffer/deref/field/rate-limit helpers.
- [`SealPile`](../impl/SealPile.md) — the value being built; where the seal is enforced at runtime.
- [`Sealable`](../aspect/Sealable.md) — the conceptual seal model (modes, redirection, `makeSetter` pattern, recipes).
- [builder index](_index.md) · [overview](../../overview.md) · [concepts/](../../concepts/).
