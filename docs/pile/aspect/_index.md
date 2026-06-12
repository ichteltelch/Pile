# `pile.aspect` — package index (Tier 1)

Source folder: `src` (all interfaces below).

The **granular aspect interfaces** — each declares one capability of a reactive value. Concrete values implement assembled combinations of these (`pile.aspect.combinations`, then `pile.impl`). This index is the navigation layer: a one-line gist per interface. Open a unit's own doc for the interface-level detail (the *delta over its javadoc* — override map, caveats, recipes); see [`../../concepts/`](../../concepts/) for cross-cutting mechanisms.

Up: [overview](../../overview.md). Cross-cutting: [concepts/transactions.md](../../concepts/transactions.md).

## Reading
- The two halves of the dependency graph are [`Dependency`](Dependency.md) (depended-on) and [`Depender`](Depender.md) (depends-on).
- Read access is [`ReadValue`](ReadValue.md); write access is [`WriteValue`](WriteValue.md); the *model* behind validity/transactions/deep-revalidate is in [concepts/transactions.md](../../concepts/transactions.md).
- An aspect method's real behavior often lives in its overrides (`PileImpl` vs `Independent` vs `SealPile` vs `JustReadValue`/`AlwaysValid`) — each unit doc flags the significant ones.

## Reading & values
- [`ReadValue`](ReadValue.md) — read the wrapped value, validity-aware: reads vary on three axes (blocking vs not, side-effecting vs pure snapshot, dependency-recording vs not).
- [`JustReadValue`](JustReadValue.md) — minimal functional read aspect (`extends ReadValue, AlwaysValid`); supply only `get`, the value is permanently valid. What `ReadValue.wrap(Supplier)` produces.

## Writing
- [`WriteValue`](WriteValue.md) — write the value via `set` (which returns the *actually-set* value); writes may be corrected, vetoed, redirected (sealing), or batched by a transaction.
- [`CorrigibleValue`](CorrigibleValue.md) — installable *correctors* (`Function`s) run on the `set` path that normalize/replace an incoming value or veto it.
- [`VetoException`](VetoException.md) — unchecked exception a corrector throws to refuse a write; its `revalidate` flag decides whether to also recompute.
- [`Sealable`](Sealable.md) — freeze writes: seal modes (throw / ignore / warn / redirect); the privileged `makeSetter` bypass **must be obtained before sealing**.
- [`WriteElsewhere`](WriteElsewhere.md) — vestigial, **entirely commented-out** marker aspect for deferred/other-thread writes; currently inert (not on the classpath).

## Dependency graph
- [`Dependency`](Dependency.md) — the value others may depend on: depender (un)registration, validity queries (`isValid` blocks/side-effects vs `isValidAsync` snapshot), the deep-revalidate registry.
- [`Depender`](Depender.md) — the value that depends on others: dependency wiring, the `dependencyBeginsChanging`/`dependencyEndsChanging` callbacks behind change propagation, essential dependencies, `deepRevalidate`.
- [`HasInfluencers`](HasInfluencers.md) — exposes a value's non-`Dependency` influencers (currently just the `owner`/derivation source); consulted only during deep-revalidation, never in change propagation.

## Validity & recomputation control
- [`AlwaysValid`](AlwaysValid.md) — mixin aspect supplying "permanently valid" `ReadValue` defaults that collapse all validity/blocking reads to plain `get`.
- [`CanAutoValidate`](CanAutoValidate.md) — aspect for values that auto-validate: recompute-if-invalid, recursing over transitive dependencies (no-op for always-valid types).
- [`AutoValidationSuppressible`](AutoValidationSuppressible.md) — aspect for temporarily suspending a value's automatic recomputation via a reference-counted, released `Suppressor`.
- [`LazyValidatable`](LazyValidatable.md) — aspect for deferring a value's recomputation until it is actually read (lazy validation); flagged immature.

## Transactions & locking
- [`DoesTransactions`](DoesTransactions.md) — client-facing transaction aspect: `transaction` returns a `Suppressor` whose release ends the batch (`PileImpl` invalidates for the duration; `Independent` stays valid). Model/internals in [concepts/transactions.md](../../concepts/transactions.md).
- [`HasInternalLock`](HasInternalLock.md) — one-method aspect letting code ask whether the current thread already holds a value's internal mutex; the framework's reentrancy/deadlock guard.

## Value memory & lifecycle
- [`RemembersLastValue`](RemembersLastValue.md) — aspect for persisting/restoring a value-holder's last value across runs, with user-driven sets remembered and programmatic sets suppressible.
- [`LastValueRememberer`](LastValueRememberer.md) — the storage strategy that stores/recalls a value's remembered "last value" (e.g. into `Preferences`); attached as a STRONG association.
- [`LastValueRememberSuppressible`](LastValueRememberSuppressible.md) — super-interface for temporarily suppressing remember-last-value via a `Suppressor`; nested `Single`/`Multi`/`None` mix-ins pick how many suppressors an implementor yields.
- [`ReferenceCounted`](ReferenceCounted.md) — reference-counting lifecycle aspect: count references, may self-destruct at zero; balanced inc/dec via `rcReferenceKeeper` → `Suppressor`.

## Misc
- [`HasAssociations`](HasAssociations.md) — typed key→value association store mixed into values (bounds, memoization, GC keep-alive references).

## Sub-packages
- [`aspect.combinations`](combinations/_index.md) — the assembled contracts (`ReadDependency` → `ReadListenDependency` → `ReadWriteListenDependency`, the capstone `Pile`, `Prosumer`, …). ✅ 12/12.
- [`aspect.recompute`](recompute/_index.md) — the recomputation machinery (`Recomputation`, `Recomputer`, `Recomputations`, dependency recorders). ✅ 5/5.
- [`aspect.listen`](listen/_index.md) — the observation machinery (`ListenValue`, `ValueListener`, `ValueEvent`, listener wrappers, multi-value observation). ✅ 10/10.
- [`aspect.transform`](transform/_index.md) — the (rudimentary) transform mechanism (`TransformHandler`, reactions, `TransformableValue`, …). ✅ 9/9.
- [`aspect.bracket`](bracket/_index.md) — value brackets (`ValueBracket`, `HasBrackets`, dependency brackets, decorators). ✅ 12/12.
- [`aspect.suppress`](suppress/_index.md) — suppression/handle utilities ([`Suppressor`](suppress/Suppressor.md), `MockBlock`, switchers, …). ✅ 7/7.
- [`aspect.limitedresource`](limitedresource/_index.md) — a small (unfinished/unused) limited-resource aspect. ✅ 2/2.
