# `pile.impl.SealPile`

The only sealable `Pile`: `PileImpl` + [`Sealable`](../aspect/Sealable.md), enforcing the seal by overriding every structural mutator to throw and routing `set` through an interceptor while a privileged depender bypasses the lock.

Source folder: `src`. File: `pile/impl/SealPile.java`.

`SealPile<E> extends `[`PileImpl<E>`](PileImpl.md)` implements `[`Sealable<E>`](../aspect/Sealable.md). This is the **impl-level** doc: it maps how the seal is *enforced* on top of `PileImpl`. For the conceptual story — what sealing means, the four seal modes (throw/ignore/warn/redirect), redirection, the `makeSetter`-before-seal pattern, and task recipes — read the aspect doc [`Sealable`](../aspect/Sealable.md), which is where that material lives. This doc does not repeat it.

## The seal as one field

The entire seal state is the single volatile field `sealed` (a `Consumer<? super E>` interceptor, ). `sealed == null` means unsealed; non-null means sealed, and the consumer *is* the seal mode. `isSealed` is exactly `sealed != null`; `isDefaultSealed` is the identity test `sealed == defaultInterceptor`. A second flag, `allowInvalidation`, records whether `permaInvalidate` is permitted while sealed.

`seal` / `seal(interceptor, allowInvalidation)` set the field once; re-sealing with the same interceptor is a no-op, with a different one throws. All of this is the `Sealable` contract — see [`Sealable` § seal modes](../aspect/Sealable.md).

## The override map — what `SealPile` changes vs `PileImpl`

`SealPile` adds **no new value/recompute machinery**; it is a guard layer. Every override below is "check `sealed`, then delegate to `super`" (the `PileImpl` behavior documented in [`PileImpl.md`](PileImpl.md)). Three categories:

### 1. `set` — redirected, not blocked
The one method that does not throw when sealed. If `sealed != null` it calls `sealed.accept(val)` and returns the current `get` (never the argument); otherwise it calls `set0(val)`. `set0` is a thin `super.set(val)` — the *only* seal-bypassing write path, used by both the privileged depender and (transitively) by `set` when unsealed.

### 2. Structural mutators — throw when sealed
Each re-checks `sealed != null` and throws `IllegalStateException` before delegating to the corresponding `PileImpl` method:

| Override | Seal behavior | Line |
|---|---|---|
| `addDependency(d, invalidate)` / `(d, invalidate, recordChange)` | throw | ,  |
| `removeDependency(d)` / `(d, invalidate, recordChange)` | throw **unless** this or `d` is destroyed | ,  |
| `setDependencyEssential` | throw |  |
| `_setRecompute` | throw |  |
| `_addCorrector` | throw |  |
| `_setEquivalence` | throw |  |
| `_addValueBracket` / `_addOldValueBracket` / `_addAnyValueBracket` | throw **unless** the bracket is a `ValueOnlyBracket` |  |
| `permaInvalidate` | throw **unless** `allowInvalidation` |  |

Note the two exceptions baked into the guards: **destroyed values/deps may still be unhooked** (`removeDependency`), and **value-only brackets are still allowed** while sealed (a sealed value can still gain a bracket that only observes the value, not the structure). `permaInvalidate` is gated by the separate `allowInvalidation` flag, not by `sealed` alone.

### 3. Setter/depender accessors — refuse when sealed, lazily create otherwise
- `makeSetter` and `getPrivilegedDepender`: throw if `sealed != null`, else lazily create the singleton `privi` (a `PrivilegedWriteDepender`) and return it. `makeSetter` narrows it to `WriteValue<E>`; `getPrivilegedDepender` returns it as `Depender`.
- `setterExists` / `privilegedDependerExists`: both are `privi != null` — i.e. "was a bypass ever handed out". The two names are aliases for the same field.

### 4. `isSealed` — the meaningful override (SUPERDOC refinement)
[`PileImpl.md` § salient behavior](PileImpl.md) states `isSealed` *always returns false*. That is true only because `PileImpl` is never sealable. **`SealPile` is precisely the override that makes `isSealed` meaningful**: it returns `sealed != null`. So the `PileImpl` "always false" claim is a base-class default that `SealPile` refines, not a contradiction.

### Other overrides
- `setName` — returns `SealPile<E>` (covariant) instead of `PileImpl`'s return; sets `avName` directly, bypassing the structural guards (renaming is always allowed).
- `destroy` — **sets `sealed = null` first**, then `super.destroy`, so the seal does not block tearing down dependencies.
- `willNeverChange` — true only when default-sealed AND no setter/privileged depender was ever made AND (`recompute == null` or `_thisDependsOn == null`). The mere existence of a setter means it could still change.

## The privileged bypass: `PrivilegedWriteDepender`

The inner class `PrivilegedWriteDepender implements `[`WriteDepender<E>`](../aspect/combinations/WriteDepender.md) is the trusted write/edit channel handed out by `makeSetter` / `getPrivilegedDepender`. It is a **proxy onto the enclosing `SealPile`** that sidesteps the seal in two distinct ways:

- **Writes** (`set`, `accept`) go through `set0` — the unguarded `super.set`. So a retained setter keeps writing after the value is sealed.
- **Structural edits** (`addDependency`, `removeDependency`, `setDependencyEssential`, `_setEquivalence`, `permaInvalidate`) call `SealPile.super.*` directly — they skip `SealPile`'s own throwing overrides by reaching past them to `PileImpl`.
- **Everything else** (transactions, revalidate, deep-destroy, dependency-callback protocol, remember-last-value, `getDependencies`, …) forwards to `SealPile.this.*` (the normal, non-overridden behavior).

So the privileged owner can do on a sealed value everything an unsealed `PileImpl` could: write, invalidate, transact, and rewire dependencies. `getPrivilegedDepender` on the proxy returns `this` — it is idempotent.

## Salient / surprising behavior
- **`super.*` vs `this.*` is load-bearing inside the proxy.** Calls that must bypass the seal use `SealPile.super.method(...)` to skip `SealPile`'s throwing override; calls that need no bypass use `SealPile.this.method(...)`. Mixing these up would either re-trigger the seal guard or skip legitimate `SealPile` behavior. ( vs .)
- **`set` returns `get`, not the argument, when sealed** — and the interceptor may have written elsewhere. See [`Sealable` § salient behavior](../aspect/Sealable.md).
- **`destroy` silently unseals**. After `destroy` the value is no longer sealed; this is internal to teardown but means `isSealed` flips to false on destruction.
- **`removeDependency(d, recompute)` on the proxy maps one boolean to both `super` params**: `SealPile.super.removeDependency(d, recompute, recompute)` — `invalidate` and `recordChange` are forced equal through this entry point.

## Caveats & gotchas
- **Get the setter before sealing.** `makeSetter`/`getPrivilegedDepender` throw once sealed; the only post-seal write channel is one retrieved earlier. (See [`Sealable` § caveats](../aspect/Sealable.md).)
- **All enforcement is runtime exceptions, not types.** A sealed `SealPile` is statically still a full `PileImpl`; every frozen mutator re-checks `sealed != null`. There is no compile-time sealed view.
- **`privi` is created lazily and is a singleton**; `setterExists` therefore conflates "setter requested" with "privileged depender requested" — they share the field.
- **The seal is one volatile field with no lock.** `seal` does a check-then-set on `sealed` without synchronization; concurrent sealing from multiple threads is not guarded beyond field volatility.

## Tech debt / warts
- **Commented-out frozen mutators** sit dead in the source: `_setCanRecomputeWithInvalidDependencies` and `setDontRetry` — the set of "what sealing freezes" is still in flux.
- **`makeSetter` returns `WriteValue<E>` but the object is a full `WriteDepender`** — callers needing more must use `getPrivilegedDepender` or cast. (Noted also in [`Sealable` § tech debt](../aspect/Sealable.md).)
- **~75 forwarding methods** in `PrivilegedWriteDepender` are hand-written delegations; any new `WriteDepender` method must be added here by hand, and the `super`-vs-`this` choice re-decided each time — easy to get wrong.
- The class has a stray double blank line / trailing whitespace and inconsistent javadoc (e.g. duplicated `@return` text on `setterExists`/`privilegedDependerExists`, ).

## Related
- [`PileImpl`](PileImpl.md) — the base; every `super.*` call resolves there. Rely on it for the underlying value/recompute/dependency semantics.
- [`Sealable`](../aspect/Sealable.md) — the aspect doc with the conceptual seal model, seal modes, redirection, and recipes (the primary doc for *using* sealing).
- [`WriteDepender`](../aspect/combinations/WriteDepender.md) — the contract `PrivilegedWriteDepender` implements. · [`Pile`](../aspect/combinations/Pile.md) — the capstone interface.
- [`AbstractReadListenDependency`](AbstractReadListenDependency.md) — the shared base under `PileImpl`. · [impl index](_index.md) · [overview](../../overview.md) · [concepts/transactions.md](../../concepts/transactions.md) — how an intercepted `set` opens a transaction.
