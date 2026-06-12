# `Functional` — assorted functional-interface helpers, constants, and small combinators (a grab-bag).

Source folder: `src` (package `pile.utils`).

A static toolbox of reusable `@FunctionalInterface` instances, singleton no-op/identity/null helpers, and a few predicate combinators. Pile reaches for these wherever the JDK's own functional types don't quite fit — chiefly to get **shared singletons** (so `==` comparison and identity-caching work), **multi-interface** instances (one object that is a `Consumer` *and* a `BiConsumer` *and* …), and **involutive** wrappers (`not(not(p)) == p`). It is not a reactive-values type; it is plumbing the core builds on. See the package [_index.md](_index.md) and the project [overview](../../overview.md); cf. sibling [Bijection.md](Bijection.md).

Everything is `static` on the class `Functional` (never instantiated). The doc below groups members by purpose rather than transcribing each javadoc.

## Singleton constants (shared instances)

These exist as named `public static final` fields so call sites can share one object and compare by identity:

- `ID` / `id()` — the identity `Function`. `ID` is the raw `Function<Object,Object>`; `id()` casts it to `Function<V,V>`. **By far the most-used member of the file** (hundreds of call sites across the core — transforms, default mappers, sealing). Reach for `id()` when you need a typed identity.
- `NULL_SUPPLIER` / `nullSupplier()` — a `NullSupplier` that is simultaneously a `Supplier<T>` returning `null` and a `Function<Object,T>` returning `null`. `nullSupplier()` is the type-safe accessor.
- `IS_NULL` / `IS_NOT_NULL` — `Predicate<Object>` null-checks. Heavily used as filter/validity predicates throughout the core.
- `CONST_TRUE` / `CONST_FALSE` — the two `ConstBool` singletons (see below).
- `ID_PREDICATE` — `Predicate<Boolean>` that just unboxes the `Boolean` (i.e. `v -> v`). Note: NPEs on a `null` argument, unlike the null-tolerant predicates above.
- `NOP` — the shared do-nothing instance (see below).

## Multi-interface "does-nothing" / "constant" objects

Two nested `final` classes each implement *several* functional interfaces at once, so a single instance can be passed wherever any of those shapes is expected:

- `Nop` (singleton `NOP`) — implements `Runnable`, `Consumer<Object>`, `BiConsumer<Object,Object>`, and `IntConsumer`; every method is empty. Private constructor; use the `NOP` field. Use it as a no-op callback/listener of whichever arity the call site wants.
- `ConstBool` (singletons `CONST_TRUE`, `CONST_FALSE`) — a constant boolean that implements `Predicate<Object>`, `Supplier<Boolean>`, `Function<Object,Boolean>`, `BooleanSupplier`, *and* `BiPredicate<Object,Object>`. Carries a public `final boolean value`. It overrides `equals`/`hashCode` by `value` (so the two singletons are value-comparable) and provides a `negate()` that returns the *other* singleton. Construct via `constPredicate(boolean)`, which hands back the appropriate singleton rather than a fresh object.

## Predicate / supplier combinators

- `not(Predicate<A>)` and `not(BooleanSupplier)` — negation wrappers. Both are **involutive**: they unwrap an inner `InversePredicate`/`InverseBooleanSupplier` instead of double-wrapping, so `not(not(p)) == p` holds (identity, not just equality). NPE if the argument is `null`. The `Predicate` overload is used pervasively in the core (notably sealing / redirected-write logic, where negating a predicate must round-trip).
- `conjunction(Predicate...)` / `disjunction(Predicate...)` — logical AND / OR over a varargs of predicates, with short-circuit evaluation. Edge cases: `null` or empty array collapses to `CONST_TRUE` (conjunction) / `CONST_FALSE` (disjunction); a single-element array is returned **as-is** (after a null-check) with no wrapper. The multi-element case clones the array and null-checks every element up front. `conjunction` is used by `FilteredBracket` (in `pile.aspect.bracket`) to AND together bracket filters.
- `constPredicate(boolean)` — see `ConstBool` above; returns a singleton.

## Null-handling function/consumer factories

Small adapters that build a new lambda each call (not singletons):

- `ifNotNull(Consumer)` — a `Consumer` that forwards to the delegate only when the argument is non-null (silently drops `null`).
- `ifNullConst(defaultValue)` — a `Function` returning its argument, or `defaultValue` when the argument is `null`.
- `ifNull(Supplier)` — same, but the fallback is lazily pulled from a `Supplier`.

## What's actually used in the core

Within the curated Pile workspace, the workhorses are `id()`/`ID`, `not(Predicate)`, `IS_NULL`/`IS_NOT_NULL`, and the `ConstBool` constants / `constPredicate`; `conjunction` is used by `FilteredBracket`. The remaining members (`NOP`, `ifNotNull`, `ifNull`/`ifNullConst`, `disjunction`, `not(BooleanSupplier)`, `nullSupplier`) are part of the offered surface but are sparsely called from the indexed code — treat them as convenience helpers, not load-bearing internals.

## Caveats & gotchas

- **Singletons matter for identity.** `ID`, `NOP`, `CONST_TRUE/FALSE`, `NULL_SUPPLIER` are deliberately shared; code may rely on `==` against them (e.g. identity-keyed caches, "is this the default mapper?" checks). Don't replace a usage with a fresh equivalent lambda where identity is being tested.
- `not(...)` involutivity is **structural** — it depends on the wrapper being the private local class, so a predicate that negates "by hand" won't be unwrapped by `not`.
- `ID_PREDICATE` is **not** null-safe (unboxing NPE), unlike the deliberately null-tolerant `IS_NULL`/`IS_NOT_NULL` and `ConstBool`. Pick the right one.
- The single-element `conjunction`/`disjunction` returns the input predicate directly, so the returned predicate's runtime type is whatever you passed in (no defensive copy of behavior). The empty/null case returns a `ConstBool`, not a plain lambda — usually irrelevant, but the result then also answers as a `Supplier`/`BiPredicate`/etc.
- `ifNotNull` **silently swallows** `null` inputs — idiomatic here, but easy to mistake for a dropped call when debugging.

## Tech debt / warts

- Class-level javadoc typo: "pogramming". The `IdentitiyMemoCache` sibling has a similar spelling slip — this codebase tolerates a few.
- The `ConstBool.hashCode()` magic numbers (`4242342` / `579475943`) are arbitrary constants; fine, just noted.
- A pure grab-bag: membership is "whatever the JDK didn't give us," so there's no organizing principle beyond utility. Expect it to grow ad hoc.
