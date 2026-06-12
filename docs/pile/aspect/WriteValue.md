# `pile.aspect.WriteValue`

Source folder: `src`. File: `pile/aspect/WriteValue.java`.

The **aspect interface a reactive value implements when it can be written to** — `set`, `setNull`, `transferFrom`, plus invalidation/revalidation and the transaction-close hooks. It is the write counterpart to the read/dependency aspects ([`ReadValue`](ReadValue.md), [`Dependency`](Dependency.md)). `WriteValue<E> extends Consumer<E>, DoesTransactions, RemembersLastValue`, so every writable value is also a `Consumer`, does transactions, and can remember/restore a last value.

See the [overview](../../overview.md) for where this sits, and [concepts/transactions.md](../../concepts/transactions.md) for how a `set` opens a transaction and propagates change. Concrete piles implement it via the assembled contracts in `pile.aspect.combinations` (`ReadWriteValue` → … → `Pile`) on top of `AbstractReadListenDependency` / `PileImpl`; sealable variants add [`Sealable`](Sealable.md)/`SealPile`.

## What it is for

`WriteValue` is the surface client code uses to **push a new value into a reactive cell**. The headline method is `set`. Unlike a plain setter, the value actually stored may differ from the value you passed (corrections), or your write may be refused/redirected entirely (veto, sealing). Most other methods manage the cell's *validity* and the transaction lifecycle around a write.

## Key methods by purpose

### Writing
- `E set(E value)` — set the value; **returns the value actually set**, which may differ from the argument because corrections are applied. See *Ways a write can be refused/ignored/redirected* below.
- `void accept(E value)` — `Consumer` adapter; just delegates to `set`.
- `WriteValue<E> setNull` — `set(null)`, returns `this` (subinterfaces narrow the return type).
- `transferFrom(ReadValue<? extends E> v, boolean alsoInvalidate)` — copy from another value: if `v` is valid, `set(v.getValidOrThrow)`; otherwise, if `alsoInvalidate`, call `revalidate`. An `InvalidValueException` from the source is swallowed.

### Validity control
- `void revalidate` — invalidate and recompute as soon as the rules allow; **a no-op for values with no concept of invalidity**.
- `void permaInvalidate` — make the value invalid and prevent immediate recomputation. (Opens a transaction internally — see [transactions § internal model](../../concepts/transactions.md).)
- `void valueMutated` — signal that the *held* object was mutated in place; fires a `ValueEvent` but **does not** cause dependers to recompute. The Javadoc explicitly advises against mutating values.

### Equivalence (change detection)
- `_setEquivalence(BiPredicate)` / `_getEquivalence` — get/set the relation used to decide whether a change actually occurred when a transaction closes. This is the same equivalence the old-value comparison uses on transaction close (see [transactions § old-value remembering](../../concepts/transactions.md)).

### Transaction close hooks (low-level — `__`)
- `__endTransaction` → `__endTransaction(true)`.
- `__endTransaction(boolean changedIfOldInvalid)` — closes a transaction; the flag says whether to treat the value as changed when the old value was invalid. Pair it yourself with `__beginTransaction`; **prefer the `Suppressor` form `transaction`** from `DoesTransactions` (see [transactions § client API](../../concepts/transactions.md)).

### Correction
- `E applyCorrection(E v)` — run the value's corrector chain and return the corrected value; delegates to [`CorrigibleValue#applyCorrection`](CorrigibleValue.md) semantics. Note: the interface declaration here drops the `throws VetoException` that `CorrigibleValue.applyCorrection` declares; the implementation catches the veto internally.

## Ways a write can be refused / ignored / redirected

This is the surprising part of `set`. A call to `set(x)` may not store `x`, or may not store anything:

1. **Correction changes the value.** Before storing, `set` runs `applyCorrection`, so the stored (and returned) value can differ from the argument (`WriteValue.java`; in `PileImpl`). Use the **return value of `set`**, not the argument, if you need the value that landed.

2. **Veto refuses the write.** A corrector may throw a `VetoException` ([`VetoException`](VetoException.md), thrown from `CorrigibleValue` correctors). On veto the write is abandoned; if `VetoException.revalidate` is set, the value triggers `revalidate` instead. A `VetoException` is *not* logged; any other corrector exception is logged at SEVERE and the change rejected (`PileImpl`, mirrors `CorrigibleValue` Javadoc). The same veto path guards recomputation.

3. **Sealing redirects the write to an interceptor.** If the value implements [`Sealable`](Sealable.md) and has been sealed, `set` does **not** store — it hands the argument to the seal interceptor and returns the current `get` (`SealPile.java:set`, the `sealed!=null` branch). The *default* interceptor throws `IllegalStateException` ("Cannot call set directly on a sealed SealableValue", `SealPile.java`); a custom interceptor can route the value elsewhere. The only way to still write a sealed value is a `WriteValue` obtained **before** sealing via `Sealable.makeSetter` (`makeSetter` itself throws if already sealed, `SealPile.java`).

4. **Last-value remembering / transactions can mask a write's visible effect.** A `set` inside an open transaction is batched: dependers don't react until the transaction closes, and if the value resolves back to an equivalent value, the old value is restored and no change is emitted (see [transactions § old-value remembering](../../concepts/transactions.md)).

## Salient / surprising behavior

- **`set` returns the actually-set value, not your argument**. With corrections or sealing this matters.
- **`valueMutated` does not trigger recomputation** of dependers — it only fires a `ValueEvent`. In-place mutation is discouraged; prefer replacing the value.
- **`revalidate` is a no-op** on values that can't be invalid (e.g. `Independent`-style holders).
- **Sealing turns `set` into a redirect, not an error-by-default-only thing**: a custom interceptor means a sealed `set` can have arbitrary side effects rather than just throwing.

## Caveats & gotchas

- The `applyCorrection` declaration on `WriteValue` **omits** `throws VetoException` even though the corrector semantics throw it; the veto is caught inside the implementation. Don't assume calling `applyCorrection` propagates a checked veto to you.
- `__endTransaction(boolean)` is a do-it-yourself primitive; mismatching begin/end leaves the value stuck in transaction (won't recompute). Use `DoesTransactions.transaction` instead.
- `transferFrom` **silently swallows** an `InvalidValueException` from the source; a transfer can quietly do nothing (and only `revalidate` if `alsoInvalidate`).
- After sealing, `set` no longer stores — code that holds a `WriteValue` reference and expects writes to land must use a setter obtained from `makeSetter` before the seal.

## Common tasks (how to…)

- **Write and capture the effective value:** `E actual = wv.set(x);` — use `actual`, since correction may have changed it.
- **Clear a value:** `wv.setNull;`.
- **Copy from another reactive value:** `wv.transferFrom(other, /*alsoInvalidate=*/true);`.
- **Batch several writes consistently:** open a transaction (`DoesTransactions.transaction`), `set` repeatedly, release — see [transactions](../../concepts/transactions.md).
- **Force recompute / mark stale:** `revalidate` (recompute when allowed) or `permaInvalidate` (invalid, no immediate recompute).
- **Keep writing a sealed value:** obtain a `WriteValue` via `Sealable.makeSetter` **before** sealing.

## Tech debt / warts

- `applyCorrection`'s signature on `WriteValue` is inconsistent with `CorrigibleValue.applyCorrection` (the `throws VetoException` is dropped); see [overview § caveats](../../overview.md) on unsystematic API.
- `WriteElsewhere` (a sibling marker interface for deferred/other-thread writes) is **entirely commented out** in `WriteValue.java`'s package (`pile/aspect/WriteElsewhere.java`) — dead/aspirational; `WriteValue` itself has no defer hook.
- Several Javadoc tags are malformed (e.g. a stray underscore in the `revalidate` block comment, `WriteValue.java`; `@see {@link CorrigibleValue}` reads oddly at ).
- The `_`/`__`-prefixed methods (`_setEquivalence`, `__endTransaction`) express access control the language can't enforce; treat `__` ones as framework-internal.

## Related

- [`CorrigibleValue`](CorrigibleValue.md) / [`VetoException`](VetoException.md) — the correction/veto mechanism behind `set`.
- [`Sealable`](Sealable.md) — write sealing/redirection and `makeSetter`.
- [`ReadValue`](ReadValue.md), [`Dependency`](Dependency.md), [`Depender`](Depender.md) — the read/dependency aspects.
- `RemembersLastValue` — last-value store/restore, a supertype of `WriteValue`.
- [concepts/transactions.md](../../concepts/transactions.md) — how a `set` opens a transaction, batching, old-value remembering.
- [overview.md](../../overview.md) — architecture map.
