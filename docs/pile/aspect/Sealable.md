# `pile.aspect.Sealable`

Source folder: `src`. File: `pile/aspect/Sealable.java`.

The **aspect interface a reactive value implements when its writes can be _sealed_** — frozen so that, from that point on, ordinary `set` calls are no longer stored but instead ignored, warned, thrown on, or redirected to an *interceptor*. It is the lock half of the write story: [`WriteValue`](WriteValue.md) is *how you write*, `Sealable` is *how writing gets locked down*. Sealing is one of the ["ways a write can be refused/ignored/redirected"](WriteValue.md) listed for `set`.

The interface declares only the seal/query surface; all real behavior lives in the concrete implementation **`SealPile<E>`** (`pile/impl/SealPile.java`), the sealable subclass of `PileImpl`. Citations below are mostly to `SealPile.java`, since the interface is a thin contract.

See the [overview](../../overview.md) for where this sits, and [concepts/transactions.md](../../concepts/transactions.md) for how a `set` (the thing sealing intercepts) opens a transaction.

## What sealing is

A `Sealable` starts mutable. You build it up — add dependencies, correctors, brackets, a recomputer, write initial values — and then call `seal` to declare *"no more ordinary changes"*. After sealing:

- ordinary `set(x)` no longer stores `x`; it hands `x` to the seal **interceptor** and returns the current `get`;
- structural mutators throw `IllegalStateException` — `addDependency` / `removeDependency`, `setDependencyEssential`, `_setRecompute`, `_addCorrector`, `_setEquivalence`, and non–value-only bracket changes;
- `permaInvalidate` throws **unless** the seal allowed invalidation.

`isSealed` reports the state; it is simply `sealed != null`.

## The seal modes

What a sealed `set` does is entirely determined by the **interceptor** `Consumer<? super E>` passed to `seal`. The mode is the choice of interceptor:

| Mode | Interceptor | Effect of a sealed `set(x)` |
|---|---|---|
| **throw** (default) | `defaultInterceptor` | throws `IllegalStateException` *"Cannot call set directly on a sealed SealableValue"* |
| **ignore** | a no-op (e.g. `Functional.NOP`) | silently drops the write |
| **warn** | a logging consumer | logs/records the rejected value, drops the write |
| **redirect** | a consumer that writes elsewhere | forwards `x` to another value |

- `seal` (no args) selects the **throw** mode — it calls `seal(defaultInterceptor, false)`.
- `seal(interceptor, allowInvalidation)` selects a custom mode; a `null` interceptor falls back to the default throw interceptor.
- `isDefaultSealed` is true iff sealed with the throw interceptor — i.e. identity-compared against `defaultInterceptor`.

The **ignore** mode is used in the framework itself: `Piles.constInvalid` seals a `SealPile` with `Functional.NOP` and `allowInvalidation=true`, giving a permanently-invalid constant whose writes are harmless no-ops.

### How redirection works

A redirecting interceptor is just a consumer that, instead of storing, performs another write. The interceptor receives the value the caller tried to `set`, so it can transform and forward it. The canonical example is a writable **negation**: a `not(x)` cell sealed with an interceptor `v -> x.set(!v)`. Writing `true` to `not(x)` is intercepted and turned into `x.set(false)` — the `not(x)` cell itself is never written directly; it stays a pure recomputed view of `x`, and the "write" lands on `x`. The same shape redirects writes through any invertible transform (offsets, field projections, unit conversions).

Because the interceptor runs *instead of* the store, a redirect is not a value-correction (those run via [`CorrigibleValue`](CorrigibleValue.md) before storing); it is a complete diversion of the write.

## The privileged bypass: `makeSetter`

Sealing locks *ordinary* writes, but the value usually still needs a trusted owner who can keep writing it (e.g. a recomputer-free cell driven by code that built it). That trusted channel is obtained from **`makeSetter`**, which returns a [`WriteValue<E>`](WriteValue.md) whose `set` calls the internal `set0` and bypasses the seal entirely (`SealPile.java`, `set0` at , the privileged `set` at ).

**The bypass must be obtained _before_ sealing.** `makeSetter` throws `IllegalStateException` if the value is already sealed. So the pattern is: build → `makeSetter` → keep the returned setter → `seal`. After that, the only way to write the value is through the retained setter; every other `set` goes to the interceptor.

- `setterExists` reports whether a setter (privileged depender) was ever handed out — it is `privi != null`.
- The setter is a singleton: repeated `makeSetter` calls return the same `PrivilegedWriteDepender`. It is the full `WriteDepender`, so the privileged owner can also revalidate, invalidate, transact, and edit dependencies on the otherwise-frozen value.

## Salient / surprising behavior

- **Sealing is permanent and one-shot per interceptor.** There is no `unseal`. Re-sealing with the *same* interceptor is a silent no-op, but re-sealing with a *different* interceptor throws `IllegalStateException`.
- **Default `set` on a sealed value throws, not no-ops.** Unless you chose ignore/warn/redirect, a stray `set` after sealing is an exception — see the [`WriteValue` write-redirection list](WriteValue.md).
- **`set` always returns the current `get` when sealed**, never the argument — consistent with `WriteValue.set` returning the actually-stored value.
- **Invalidation is gated separately.** Even a sealed value can be `permaInvalidate`d if `seal(..., allowInvalidation=true)` was used; the default `seal` forbids it.
- **`destroy` quietly unseals first** so dependencies can be torn down — it sets `sealed = null` before delegating.
- **`willNeverChange` is conservative.** It returns true only when default-sealed, *and* no privileged depender/setter was ever made, *and* there is no recomputer or no dependencies; the existence of a setter alone means the value might still change.

## Caveats & gotchas

- **Forgetting `makeSetter` before sealing locks you out.** Once sealed you cannot obtain a bypass; the value is writable only through a setter retrieved earlier. Hold that reference.
- **`isDefaultSealed` uses identity**, comparing against the single `defaultInterceptor` instance. Passing your own throwing consumer is *not* "default sealed" and will report `false`.
- **A redirect/ignore interceptor can swallow writes silently.** With ignore or a misrouted redirect, `set` appears to succeed (returns a value) but the cell never changes — debugging this looks like a phantom write. Prefer the default throw mode unless you specifically want diversion.
- **Sealing freezes structure, not just value.** After sealing you cannot add/remove dependencies, correctors, the recomputer, equivalence, or (non-value-only) brackets — all throw. Configure everything before sealing.
- **Interceptors run on the writing thread** inside the normal `set` path; a redirect that writes back into the same dependency graph can re-enter — keep interceptors simple.

## Common tasks (how to…)

- **Make a value read-only after setup (hard):** `v.seal;` — later `set`s throw.
- **Make a value read-only but ignore stray writes:** `v.seal(Functional.NOP /* or any no-op */, false);` (the pattern `Piles.constInvalid` uses, `Piles.java`).
- **Keep a private write channel after sealing:** `WriteValue<E> setter = v.makeSetter; … v.seal; … setter.set(x);` — obtain the setter **before** `seal`.
- **Redirect writes to another value:** `v.seal(x -> other.set(transform(x)), false);` (e.g. a writable `not(x)` sealed with `b -> x.set(!b)`).
- **Allow invalidation on a sealed value:** seal with `seal(interceptor, /*allowInvalidation=*/true)`, then `permaInvalidate` is permitted.
- **Build-and-seal in one step:** the seal-pile builders carry `seal(...)`/`makeSetter` through and seal on `build`.
- **A sealed constant with a value:** `Piles.sealedConstant(value)` sets then default-seals.

## Tech debt / warts

- **`makeSetter` returns `WriteValue<E>` but yields a full `WriteDepender`** — the interface deliberately narrows a much richer privileged object to the write surface; callers who need more must cast or use `getPrivilegedDepender`.
- **All access control is by exception at runtime**, not by type: a sealed `SealPile` is still statically a full `PileImpl`, and every frozen mutator re-checks `sealed != null` and throws. There is no compile-time "sealed view".
- **Commented-out frozen mutators** (`_setCanRecomputeWithInvalidDependencies`, `setDontRetry`) sit dead in the source, suggesting the set of "what sealing freezes" is still in flux.
- Several Javadoc tags on the interface are empty/typo'd (e.g. *"Restrict further manipulation **if** the value"* at `Sealable.java`, ; bare `@return` at , , ), consistent with the [project-wide note on unsystematic API](../../overview.md).

## Related

- [`WriteValue`](WriteValue.md) — the write aspect sealing intercepts; sealing is one of its [write-redirection paths](WriteValue.md).
- [`CorrigibleValue`](CorrigibleValue.md) — value *correction* (runs before storing), distinct from a seal *redirect* (runs instead of storing).
- [`Dependency`](Dependency.md) — the dependency surface that sealing freezes the editing of.
- [concepts/transactions.md](../../concepts/transactions.md) — how the `set` that sealing intercepts opens a transaction.
- [overview.md](../../overview.md) — architecture map; `SealPile` lives in `pile.impl`.
