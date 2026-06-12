# `ReadWriteListenDependency`

The full non-recompute reactive contract: read + write + observe + be-depended-on, unioned into one interface — everything a `Pile` is *except* recomputation.

Source folder: `src` · package `pile.aspect.combinations` · [package index](_index.md) · [overview](../../../overview.md)

## What it's for

`ReadWriteListenDependency<E>` is the combination interface that bundles the four core aspects of a reactive value:

- [`ReadValue`](../ReadValue.md) — read the (possibly invalid) wrapped value
- [`WriteValue`](../WriteValue.md) — `set` the value
- [`ListenValue`](../_index.md) — register `ValueListener`s / fire change events
- [`Dependency`](../Dependency.md) — be depended on by other reactive values (the dependency-graph node others wire to)

It is the immediate super-interface of [`Pile`](Pile.md). The split is deliberate: this interface is the *complete contract minus recomputation*; `Pile` adds recomputation (a `Recomputer`/`Recomputation`), correctors, transformation, lazy/auto validation, transactions-as-depender, etc. on top. If you have a value that can be read, written, listened to, and depended on but does **not** recompute itself (e.g. an [`Independent`](../../../overview.md)), this is the contract that describes it.

## How the union is assembled (the chain)

This interface adds **`WriteValue`** to [`ReadListenDependency`](ReadListenDependency.md). Its three declared parents collectively pull in every needed aspect:

```
ReadWriteListenValue<E>      (ReadValue + WriteValue + ListenValue)
ReadListenDependency<E>      (ReadValue + ListenValue + Dependency)   ← the read-only sibling
ReadWriteDependency<E>       (ReadValue + WriteValue + Dependency)
        └─► ReadWriteListenDependency<E>
```

So relative to the read-only sibling [`ReadListenDependency`](ReadListenDependency.md), the delta added here is exactly **write access** (`WriteValue`, via the other two parents). The two parents `ReadWriteListenValue` and `ReadWriteDependency` are where most of the inherited *concrete* convenience lives (writable buffers / `biject*` family, respectively); this interface itself adds very little.

## Methods added here (the delta over the parents)

The interface body is tiny — almost everything is inherited. What it declares/refines:

- **`set(E v)`** — re-declared `@Override` (narrows nothing; the real contract is `WriteValue.set`, which returns the *actually-set* value after corrections/redirection). Concrete implementations live in the impl classes (`PileImpl`, `SealPile`, `Independent`), not here.
- **`setNull`** — default; calls `set(null)` and returns `this` re-typed as `ReadWriteListenDependency<E>` (covariant-return refinement of the same default on `ReadWriteDependency`).
- **`fallback(E v)`** — default; `Piles.fallback(this, v)`. Makes a [`SealPile`](Pile.md) that takes the constant `v` whenever `this` is invalid, **but writes to the returned `SealPile` are redirected back into `this`**. This is the writable counterpart to the read-only `fallback` on `ReadListenDependency` (which makes a non-redirecting fallback).
- **`_setDebugCallback(DebugCallback dc)`** — declared here (and re-declared on `Pile`); installs a debug monitor. **No-op unless `DebugEnabled.DE` is `true`** (conditional-compilation flag; see [overview](../../../overview.md)). Concretely implemented once in the shared base `AbstractReadListenDependency._setDebugCallback`, so all impls share it.
- **`asDependency`** — default returns `this` (this value *is already* a `Dependency`, so no validBuffer wrapping is needed — overriding the `ReadWriteListenValue` default that would otherwise memoize a `validBuffer`).
- **`validNull`** — default delegates to `ReadListenDependency.super.validNull` (resolves the diamond: `validNull` is inherited via two paths, so it explicitly picks the `ReadListenDependency` one).

## Relationship to `Pile` (the recompute-adding subtype)

[`Pile<E>`](Pile.md) `extends ReadWriteListenDependency<E>` and layers on the recomputation machinery and several more aspects (`CorrigibleValue`, `CanAutoValidate`, `LazyValidatable`, `TransformableDependency`, `WriteDepender`, …). `Pile` re-declares `set`, `setNull`, `validNull`, `asDependency`, and `_setDebugCallback` purely to **narrow the covariant return type** to `Pile<E>` / give them `Pile`-flavored javadoc — the behavior is the same contract declared here. Mental model: *this interface = a writable, observable, depended-on value; `Pile` = that, plus it knows how to recompute itself.*

## Caveats & gotchas

- **`fallback` redirects writes.** Unlike `ReadListenDependency.fallback` (read-only), the `SealPile` returned here forwards `set` calls into `this`. Easy to forget when you only wanted a read-side default. See `FallbackTest` for the redirect behavior.
- **`_setDebugCallback` silently does nothing in production builds** (`DebugEnabled.DE == false`). Don't rely on it for non-debug logic.
- **`set` return value matters.** Inherited from `WriteValue`: the returned value is what was *actually* stored after correctors/sealing/redirection, which may differ from the argument. Don't assume `set(x)` leaves the value at `x`.
- This interface has **no field/state of its own** — it is pure aspect glue. All concrete behavior is in `AbstractReadListenDependency` and the leaf impls.

## Common tasks

- **Make a writable default-on-invalid view:** `rwld.fallback(defaultValue)` → writable `SealPile` redirecting back to `rwld`.
- **Maintain a 1-to-1 mapped writable view:** use the `biject*` family (inherited from [`ReadWriteDependency`](ReadWriteDependency.java)).
- **Get a writable validity-buffered copy:** `writableValidBuffer` / `writableBuffer` (inherited from `ReadWriteListenValue`).
- **Treat this as a graph node:** just pass it where a `Dependency` is wanted, or call `asDependency` (returns `this`).

## Tech debt / warts

- Heavy use of `@Override`-redeclaration purely for covariant returns across the `ReadValue→…→Pile` chain inflates the apparent API; the actual added surface is small.
- The `validNull`/`asDependency` defaults exist only to disambiguate diamond inheritance, not to add behavior — a maintenance hazard if a parent default changes.

## See also

- Read-only sibling: [`ReadListenDependency`](ReadListenDependency.md)
- Recompute-adding subtype: [`Pile`](Pile.md)
- Aspects unioned: [`ReadValue`](../ReadValue.md) · [`WriteValue`](../WriteValue.md) · [`Dependency`](../Dependency.md)
- Concepts: [transactions / validity](../../../concepts/transactions.md)
