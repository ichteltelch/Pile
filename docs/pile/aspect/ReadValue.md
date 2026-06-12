# `pile.aspect.ReadValue`

Source folder: `src`. File: `pile/aspect/ReadValue.java`.

The **aspect interface a reactive value implements when its plain value can be read.** It is the read-side counterpart to `WriteValue` and the value-content sibling of [`Dependency`](Dependency.md) (which exposes the *dependency-target* surface). `ReadValue<E> extends Supplier<E>, DoesTransactions`, so every readable value is a `java.util.function.Supplier` and participates in [transactions](../../concepts/transactions.md).

See the [overview](../../overview.md) for where this sits; concrete piles implement it via the assembled contracts in `pile.aspect.combinations` (`ReadDependency` → `ReadListenDependency` → `Pile`) on the `AbstractReadListenDependency` base. The minimal "nothing fancy" implementation is `JustReadValue` (a `@FunctionalInterface` that fills in transaction/lifecycle no-ops).

## What it is for

`ReadValue` packages everything a client needs to *read* a value while respecting its **validity** (a value may be valid or invalid — see the [core mental model](../../overview.md)). It offers several reads that differ along three axes: **do I block until valid**, **does observing invalidity have side effects**, and **do I want this read recorded as a dynamic dependency**. It also exposes validity/null/transaction predicates, both as one-shot booleans and as reactive boolean values.

## Key methods by purpose

### Plain reads
- `get` — the current value; **if invalid, returns `null` and the invalidity becomes *observed***. The access **may be recorded by a `DependencyRecorder`**, and on a lazy-validating value it may **trigger a `Recomputation`** that, if fulfilled synchronously, makes the value valid before returning. Not side-effect free.
- `getAsync` — "just get it, without locking or fancy stuff"; if invalid returns `null` but invalidity is **not** observed and the read is **not** recorded. Use when you must not block or cause effects.

### Validity-aware reads
- `getValidOrThrow` — returns the value if valid, else throws the checked `InvalidValueException`. The Javadoc itself recommends gating with `isValid` first to avoid throwing in the common case, and even flags the resulting duplication: *"Feel free to do something about that code duplication."*.
- `getOldIfInvalid` — if valid returns the value; if invalid, **the invalidity becomes observable and the *old* value is returned** (the pre-transaction snapshot — see [transactions § old-value remembering](../../concepts/transactions.md)), or `null` if there is no old value.

### Blocking gets (wait until valid)
- `getValid` / `getValid(long timeout)` — block until the value is valid, optionally bounded by `timeout` ms; both default-delegate to the `WaitService` overloads via `WaitService.get`. Both `throws InterruptedException`.
- `getValid(WaitService ws)` / `getValid(WaitService ws, long timeout)` — the abstract core; the host's injectable `WaitService` decides *how* to wait. **On timeout, `null` is returned and the invalidity becomes observable**.

### Validity / null / transaction predicates
- `isValid` — current validity; **if invalid, the invalidity becomes observable**, and the call **may record a dependency on `validity`**. Not a pure snapshot.
- `isValidAsync` — plain validity snapshot, **no locking, no triggered actions, no lazy-validation**. The no-side-effect counterpart of `isValid`.
- `validity` — a lazily-initialized reactive `ReadListenDependencyBool` tracking *observed* validity. **Asymmetric:** observed-valid flips to `true` as soon as the value is actually valid, but flips to `false` only *some time later* — when invalidity is actually observed (e.g. by `isValid`/`get*`), or not at all if validity returns in time.
- `isValidNull` — atomically: holds a `null` reference **but is valid**. `validNull` — the reactive boolean form; the default returns a bare `JustReadValueBool` lambda, and subclasses are expected to override with an observable type.
- `isNull` = `get==null` / `isNonNull` = `get!=null` — shorthands. **Note both call `get`**, so they share all of `get`'s side effects (observe invalidity, record, maybe recompute), and `isNull` is `true` for an *invalid* value too. `nullOrInvalid` — abstract reactive boolean covering "null **or** invalid".
- `isInTransaction` / `inTransactionValue` — one-shot and reactive views of whether a transaction is open; the transaction machinery itself lives in `DoesTransactions`/ARLD (see [transactions](../../concepts/transactions.md)).

### Convenience / lifecycle / static
- `is(E v)` — valid-and-equal test; compares via `Objects.equals` after `get` (and routes `null` through `isValidNull`), but **implementors may use a different equivalence relation**.
- `transferTo(WriteValue<? super E> v, boolean alsoInvalidate)` — copy contents to another value; if this is invalid and `alsoInvalidate` is set, calls `v.revalidate`. Swallows `InvalidValueException` internally.
- `doOnceWhenValid(Consumer<? super E> what)` — run `what` with the value as soon as it is valid; returns `null` if it could fire immediately, otherwise a removable `ValueListener` for cancellation. Listens on `validity` and re-arms itself on a spurious invalid; handles the `PleaseReAdd` re-add protocol.
- `isDestroyed` — whether the object has been destroyed and must no longer be used.
- `static wrap(Supplier<E>)` — adapt any `Supplier` into a `JustReadValue<E>`.
- `static class InvalidValueException extends Exception` — checked, thrown by `getValidOrThrow`.

## Salient / surprising behavior

- **Most reads have side effects.** `get`, `isValid`, `isNull`/`isNonNull`, `getOldIfInvalid` can all *observe* invalidity, *record* a dependency, and (for `get`) *trigger a recomputation*. The side-effect-free trio is `getAsync`, `isValidAsync`, and `getValidOrThrow`-after-`isValid`-guard.
- **`null` is overloaded.** A `null` from `get` can mean "valid null value" *or* "invalid". Disambiguate with `isValidNull` / `validNull` (valid-null) vs. `nullOrInvalid`.
- **`validity` is intentionally asymmetric / lazy**: it lags going invalid. It tracks *observed* validity, not raw validity, so it can stay `true` while the value is briefly actually-invalid if nobody observes the gap.
- **Recording is implicit.** Calling `get`/`isValid` from inside a recomputation that is recording dynamic dependencies silently registers a dependency edge (via `DependencyRecorder`). Reading "to peek" without forming a dependency requires the `*Async` variants.

## Caveats & gotchas

- **Don't use `get` to test for null** if you don't want to observe invalidity or form a dependency — `isNull`/`isNonNull` route through `get` and inherit every side effect.
- **`getValidOrThrow` is designed to be guarded.** Use the `isValid`-then-try pattern from its Javadoc rather than relying on the throw as control flow.
- **Blocking gets need a sane `WaitService`** and throw `InterruptedException`; a timeout yields `null` *and* marks invalidity observable — a `null` return there is ambiguous with a valid-null.
- **`validNull`'s default loses observability** — it returns a plain `JustReadValueBool` lambda; only overriding subclasses give you a watchable value.
- `transferTo` silently ignores `InvalidValueException`; a failed transfer is indistinguishable from a no-op unless you check validity yourself.

## Common tasks (how to…)

- **Read a value, forming a dependency (inside a recomputation):** `get`.
- **Peek without side effects / without forming a dependency:** `getAsync` (value) or `isValidAsync` (validity).
- **Read only if valid, else handle invalidity:** `if (v.isValid) { try { use(v.getValidOrThrow); } catch (InvalidValueException e) { … } }`.
- **Wait for a value (bounded):** `v.getValid(timeoutMs)` (handle `InterruptedException`; `null` ⇒ timed out).
- **React once when it next becomes valid:** `v.doOnceWhenValid(value -> …)` (keep the returned `ValueListener` to cancel).
- **Observe validity reactively:** subscribe to `validity`; for null-ness use `validNull` / `nullOrInvalid`.
- **Adapt a plain `Supplier`:** `ReadValue.wrap(supplier)`.

## Tech debt / warts

- `getValidOrThrow`'s Javadoc openly acknowledges the boilerplate it forces and invites a fix: *"Feel free to do something about that code duplication."*.
- A commented-out `getWithValidity` returning `Maybe<E>` hints at an intended unified value-plus-validity read that was never landed.
- `validNull`'s default sacrifices observability, pushing the real contract onto subclasses.
- Several `@return`/`@param` Javadoc tags are empty, consistent with the project-wide note that some API is unsystematic (see [overview § caveats](../../overview.md)).

## Related

- [`Dependency`](Dependency.md) / [`Depender`](Depender.md) — the dependency-graph aspects; `Dependency` also exposes `isValid`/`isValidAsync`/`isInTransaction` from the graph side.
- [concepts/transactions.md](../../concepts/transactions.md) — validity propagation, the *old value* returned by `getOldIfInvalid`, and `isInTransaction`/`inTransactionValue`.
- [overview.md](../../overview.md) — architecture map and the valid/invalid mental model.
