# `pile.aspect.combinations.ReadListenDependency`

The combination contract for a value you can **read**, **observe (listen to)**, and **depend on** — `ReadListenValue` ∪ `ReadDependency` ∪ `HasBrackets`.

Source folder: `src`. File: `pile/aspect/combinations/ReadListenDependency.java`.

This is the middle rung of the `pile.aspect.combinations` assembly ladder: [`ReadDependency`](ReadDependency.md) → **`ReadListenDependency`** → `ReadWriteListenDependency` → the capstone `Pile`. See the [overview](../../../overview.md) and the [aspect index](../_index.md) for where the aspect interfaces it unions live.

## What it is for

`ReadListenDependency<E>` is the contract a reactive value satisfies once it can be **read** (validity-aware [`ReadValue`](../ReadValue.md) surface), **observed** (the `ListenValue` listener surface, via `ReadListenValue`), **and depended on** (the [`Dependency`](../Dependency.md) graph surface, via `ReadDependency`). In other words it is "[`ReadDependency`](ReadDependency.md) plus the ability to attach `ValueListener`s and value brackets." Most things you treat as an observable read-only reactive value are typed as this (or its `Pile` subtype).

## Aspects it unions (the inheritance chain)

```
ReadListenDependency<E>
 ├─ ReadListenValue<E>              (= ReadValue<E> + ListenValue)      ← adds observation
 ├─ ReadDependency<E>              (= ReadValue<E> + Dependency)        ← adds depend-on + map/field/compare helpers
 └─ HasBrackets<ReadListenDependency<? extends E>, E>  ← adds ValueBracket attachment
```
(declaration at `ReadListenDependency.java`)

- The **`ReadValue`** read surface is shared by both `ReadListenValue` and `ReadDependency` (diamond — same aspect, no conflict). See [`ReadValue`](../ReadValue.md).
- **`ListenValue`** is the `pile.aspect.listen` aspect that adds listener registration/firing (`addValueListener`, `fireValueChange`, the `ValueEvent`/`ValueListener` machinery). *(Not yet documented — `pile.aspect.listen` doc pending.)* `ReadListenValue` layers `await`/`runWhen`/`doOnce…` style convenience and the buffer/rate-limit recipes on top.
- **`Dependency`** is the depended-on graph half. See [`Dependency`](../Dependency.md).
- **`HasBrackets`** adds the `_addValueBracket`/`_addOldValueBracket`/`_addAnyValueBracket`/`bequeathBrackets` surface (open/close side-effects tied to a value becoming current/old). The `Self` type parameter is bound to `ReadListenDependency<? extends E>`, which is why bracket consumers see this type.

## Methods it adds (delta over its supertypes)

Only a handful of members are *declared here* — most of the surface is inherited.

### Derived boolean/validity views (default methods)
- `validNull` — observable `ReadListenDependencyBool` that is true iff this value **is valid but holds `null`**. Built from `validity` and `readOnlyValidBuffer_memo` via `Piles.sealedNoInitBool.recompute(...).whenChanged(...)`.
- `nullOrInvalid` — **overrides** the abstract `ReadDependency.nullOrInvalid` (which `ReadValue` also declares) with the observable implementation: true iff invalid **or** null. This is the concrete landing spot for that contract — the supertypes only declare it.
- `validNonNull` — shorthand for `nullOrInvalid.not`.

These three are the observable counterparts to `ReadValue`'s one-shot `isValidNull` / `isNull` predicates: use these when you want to *watch* null/validity, not just sample it. Note the **overloaded-null gotcha** carried in from [`ReadValue`](../ReadValue.md): a bare `get==null` cannot distinguish "valid null" from "invalid" — these helpers disambiguate.

### Other
- `fallback(E v)` — make a `SealPile<E>` that follows this value but takes on the constant `v` whenever this value is invalid (`ReadListenDependency.java`; delegates to `Piles.fallback`).
- `setName(String s)` — **declared abstract here** with a covariant return of `ReadListenDependency<E>`. The Javadoc explicitly instructs subtypes to re-declare it returning their own type. The actual name-storage implementation does **not** live in the aspect base — see the override map below.
- `asDependency` — **overridden** to `return this`. This refines `ReadListenValue.asDependency`, whose default returns `validBuffer_memo` (a *buffer*, not `this`). Because a `ReadListenDependency` already **is** a `Dependency`, it returns itself instead of materialising a buffer — an important behavioral refinement (see conflicts below).

## Override map

- **`setName(String)`** — declared abstract here (covariant return). It is *not* implemented by the shared base `pile.impl.AbstractReadListenDependency`; each concrete value supplies it, re-narrowing the return type: `PileImpl`, `SealPile`, `Independent`, `Constant`, `AbstractValueList`, plus the whole specialized family (`PileBoolImpl`, `SealBool`, `IndependentInt`, `SealString`, …) and the parallel `combinations` interfaces (`Pile.setName` at `Pile.java`, `ReadListenDependencyBool.setName`). This is the project's standard self-typed-builder-style covariant-return pattern, needed because Java lacks a self type.
- **The listener / bracket / await machinery it inherits is implemented once in `pile.impl.AbstractReadListenDependency`** — `addValueListener`, `fireValueChange`, `_getListenerManager`, the three `_add*ValueBracket` methods, `bequeathBrackets`, and `await(WaitService, …)`, `dependencyName`. So this interface contributes contract; ARLD contributes behavior. *(ARLD doc pending — do not deep-read it just for this.)*
- **`nullOrInvalid` / `validNull` / `validNonNull`** — the defaults here are typically used as-is; subclasses rarely override them.

## Salient / surprising behavior

- **`asDependency` returns `this`, not a buffer**. `ReadListenValue`'s default makes a memoized valid-buffer instead; once you are a real `Dependency` that indirection is unnecessary. If you call `asDependency` polymorphically, the runtime type matters.
- **`nullOrInvalid` is where an abstract contract becomes concrete.** [`ReadValue`](../ReadValue.md)/[`ReadDependency`](ReadDependency.md) only *declare* `nullOrInvalid`; this interface is the first point in the chain that gives it an observable implementation.
- **The boolean helpers build fresh `Pile`s every call** (`Piles.sealedNoInitBool...whenChanged(...)`). They are not cached; calling `validNull` twice yields two independent reactive booleans. Hold a reference if you need a stable one.
- `validNull`/`nullOrInvalid` depend on `readOnlyValidBuffer_memo` (the *memoized* valid buffer from `ReadListenValue`), so they observe the buffered, settled value rather than racing the live one.

## Caveats & gotchas

- **Don't expect `setName` here to do anything** — it is abstract. Storage and the debug-name semantics are entirely in the concrete subclass; the aspect only fixes the covariant return type.
- **Buffer-memoization caveats apply** to the helpers that lean on `readOnlyValidBuffer_memo` — see `ReadListenValue`'s contract on when memoization is inappropriate (debug names, never-removed listeners, independent-entity semantics).
- The vast read/map/compare/field surface you can call on a `ReadListenDependency` value mostly comes from [`ReadDependency`](ReadDependency.md) and [`ReadValue`](../ReadValue.md), not from this file — look there for `map`, `field`, `isEqual`, `compareTo`, `readOnly`, `overridable`, etc.

## Common tasks (how to…)

- **Observe whether a value is null/invalid reactively:** `v.nullOrInvalid` (true on null OR invalid); `v.validNonNull` for the negation; `v.validNull` for "valid AND null".
- **Get a value that substitutes a default when invalid:** `v.fallback(defaultValue)`.
- **Treat a readable value as a dependency target:** `v.asDependency` (returns `v` itself).
- **Attach a listener / value bracket:** use the inherited `ListenValue` / `HasBrackets` surface (implemented in ARLD).
- **Map / extract a field / compare:** inherited from [`ReadDependency`](ReadDependency.md).

## Tech debt / warts

- `setName`'s "subclasses must override to narrow the return type" pattern is repeated across dozens of classes (77 `setName` hits across the workspace) purely to emulate a self type — boilerplate the project acknowledges as part of its no-extension-methods design.
- Several `@return`/`@param` Javadoc tags on the default methods are empty, consistent with the project-wide note that some API is unsystematic (see [overview § caveats](../../../overview.md)).

## Related

- [`ReadDependency`](ReadDependency.md) — the read+depend rung directly below this; source of the `map`/`field`/compare/`readOnly` surface and the abstract `nullOrInvalid` this refines.
- [`ReadValue`](../ReadValue.md) · [`Dependency`](../Dependency.md) — the underlying aspects (plus the `ListenValue` listen aspect, doc pending).
- [aspect index](../_index.md) · [overview](../../../overview.md) · [concepts/](../../../concepts/) (e.g. [transactions](../../../concepts/transactions.md) for the validity model the null/valid helpers observe).
