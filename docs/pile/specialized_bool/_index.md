# `pile.specialized_bool` — package index (Tier 1)

The **exemplar primitive-specialization family**: a parallel hierarchy of `*Bool` reactive values that mirror the generic `pile.impl`/`pile.aspect` stack but are typed to `Boolean` and add boolean-specific operators.

Source folder: `src` (all classes below).

Up: [overview](../../overview.md). Generic counterparts: [`../impl/_index.md`](../impl/_index.md). Aspects: [`../aspect/_index.md`](../aspect/_index.md).

> This is the **reference family**. The four siblings — `specialized_int`, `specialized_double`, `specialized_String`, `specialized_Comparable` — follow the exact same layout; their indexes record only the *deltas* (see "Reading the other families" below). Read this one first.

## The specialization pattern — "generality in, specialization out"

Java has no extension methods and no generics over primitives, so you cannot add `.not()` / `.and()` / `.sum()` to a plain `Pile<Boolean>` / `Pile<Integer>` from the outside. Pile's answer (per the README's *"generality in, specialization out"*): for each primitive or refined element type `X`, **mirror the whole reactive stack** — aspects, combinations, and concrete impls — as a parallel set of `*X` types. A user who declares values as the `*X` types gets:

- **typed values** — reads/writes are `boolean` (still boxed `Boolean` under the hood, `null` allowed), not opaque `Object`;
- **X-specific operators as methods** — e.g. `not()` on every `*Bool`, plus the algebra in [`PileBool`](PileBool.md) (`and`/`or`/`implies`/`equalTo`/…);
- **narrowed return types** — every override that returns a self-type or a derived value returns the `*Bool` flavour, so chained builder/operator calls stay in the typed world.

The mechanism is plain inheritance: **every `*Bool` type IS-A the matching generic type** and additionally implements the `*Bool` combination interfaces. For example `SealBool extends SealPile<Boolean> implements ReadWriteListenDependencyBool, PileBool`. Nothing about the *reactive semantics* changes — validity, transactions, sealing, recomputation, brackets are all inherited unchanged from the generic class. The `*Bool` layer only narrows types and bolts on boolean methods. **So for behavior, read the generic doc; for the operator surface, read [`PileBool.md`](PileBool.md).**

The default boolean operators (e.g. `not()`) are declared once on the combination/`PileBool` interfaces as `default` methods; the concrete classes mostly just **memoize** the derived value (see `IndependentBool.not`, `PileBoolImpl.not` — double-checked locking on `mutex`) or specialize it for their nature (`ConstantBool.not` returns a constant via `threeWay`).

## Concrete types — each maps to one generic counterpart

| `*Bool` type | extends (generic) | role |
|---|---|---|
| [`PileBoolImpl`](PileBoolImpl.md) | [`PileImpl`](../impl/PileImpl.md) | the default full reactive boolean (recompute, deps, transactions); implements [`PileBool`](PileBool.md). Covered by `PileBool.md`. |
| [`SealBool`](SealBool.md) | [`SealPile`](../impl/SealPile.md) | the sealable boolean; the type `PileBool.not(…)` returns (writing the result writes the inverse back). |
| [`ConstantBool`](ConstantBool.md) | [`Constant`](../impl/Constant.md) | never-changing boolean; always valid, silently ignores writes; `not()` returns a `Constant` (`threeWay(FALSE,TRUE,NULL_B)`). |
| [`IndependentBool`](IndependentBool.md) | [`Independent`](../impl/Independent.md) | always-valid, non-recomputing leaf boolean; `setNull`/`setName` typed; memoizes `not()`. |
| [`MutBool`](MutBool.md) | *(none — see below)* | a plain mutable `boolean` box; **not a graph node**. |
| [`SuppressBool`](SuppressBool.md) | `IndependentBool` | a reference-counted suppression flag value. |

Notes per type:

- **`ConstantBool`** — thin specialization of [`Constant`](../impl/Constant.md). `setNull`/`setName` are no-ops returning `this` (constants are immutable and unnamed), matching `Constant`'s silent-ignore idiom. `not()` is itself constant.
- **`IndependentBool`** — thin specialization of [`Independent`](../impl/Independent.md). Adds typed `setNull`/`setName` and a memoized `not()` (built once under `Recomputations.withoutRecomputation()`).
- **`SealBool`** — thin specialization of [`SealPile`](../impl/SealPile.md). Implements both `ReadWriteListenDependencyBool` and `PileBool`. This is the value type the redirecting `not`/comparison factories on `PileBool` hand back, so the inverse is itself sealable/redirecting.
- **`PileBoolImpl`** — confirmed to be simply [`PileImpl`](../impl/PileImpl.md)`<Boolean>` + `PileBool`, with typed `setName`/`setNull` and a memoized `not()`. No reactive behavior of its own. (Detailed in [`PileBool.md`](PileBool.md).)
- **`MutBool`** — does **not** specialize a reactive impl. It is a bare mutable `boolean` field (`val`) implementing the *functional* aspects only (`JustReadValueBool`, `BooleanSupplier`, `Prosumer<Boolean>`). It is the boolean analogue of [`MutRef`](../impl/MutRef.md) / `MutInt`: a closure/out-param box, always valid, no dependency graph participation. `Mut*` exists in the `bool` and `int` families but **not** in `String`/`Comparable`.
- **`SuppressBool`** — specializes the *concept* of a reference-counted automatic flag rather than a plain generic impl: it extends `IndependentBool`, seals itself, and is `false` unless one or more `Suppressor`s from `suppress()` are outstanding (then `true`). Direct writes are `@Deprecated` and fail (it is set only via its internal counter). It also vends a `suppressBracket(…)` that suppresses while a value matches a predicate. This is the boolean dual of [`AutoValidationSuppressible`](../aspect/_index.md) / [`aspect/suppress`](../aspect/suppress/_index.md)'s `Suppressor` model. `Suppress*` exists in the `bool` and `int` families but **not** in `String`/`Comparable`.

## Companion docs

- [`PileBool.md`](PileBool.md) — the **`PileBool` interface and the boolean operator algebra**: `not`/`and`/`or`/`implies`/`equalTo` and the static redirecting factories (`not(…)` → `SealBool`). The capstone of the family.
- [`combinations/_index.md`](combinations/_index.md) — the **12 `*Bool` combination interfaces** (`ReadValueBool` → `ReadDependencyBool` → `ReadListenDependencyBool` → `ReadWriteListenDependencyBool`, plus `JustReadValueBool`, `LastValueRemembererBool`, the now-inert `WriteElsewhereBool`, …): each narrows the matching `pile.aspect.combinations` contract to `Boolean` and declares the boolean operators the concrete classes inherit.

## Reading the other families (int / double / String / Comparable)

Each sibling package has the identical shape: a `Pile<X>Impl`, `SealX`, `ConstantX`, `IndependentX` mapping 1:1 to `PileImpl`/`SealPile`/`Constant`/`Independent`, plus a `PileX` interface (operator algebra) and an `X.combinations` sub-package mirroring `pile.aspect.combinations`. Their indexes therefore **do not re-explain this pattern** — they only record the deltas:

- which optional members exist (`Mut*` and `Suppress*` are present for `bool`/`int` only);
- the type-specific operator set on `PileX` (arithmetic `sum`/`product`/comparisons for `int`/`double`; `compareTo`-based ops for `Comparable`; string ops for `String`);
- any type-specific `null`/identity handling.

Everything else — inheritance structure, narrowed return types, memoized derived operators, the silent-ignore/no-op idioms — is exactly as documented here.
</content>
