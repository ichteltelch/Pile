# `pile.specialized_String` — package index (delta over the bool exemplar)

The `String`-specialized reactive family: the same parallel `*X` stack as the exemplar [`specialized_bool`](../specialized_bool/_index.md), but typed to `String` — and the **thinnest** of the families, because most of its surface is inherited and the type-specific operator (concatenation) lives in [`Piles`](../impl/Piles/_index.md), not on the value types.

Source folder: `src` (all classes below).

Up: [overview](../../overview.md). **Read the [`specialized_bool` index](../specialized_bool/_index.md) first** — it explains the "generality in, specialization out" pattern (mirror the whole stack per element type; plain inheritance; narrowed return types; memoized derived ops; silent-ignore/no-op idioms). This index records only the **String deltas**.

## Delta 1 — two fewer top-level types (5, not 7)

Reference types do **not** get the mutable-box or suppression-flag specializations, so this package has **no `MutString` and no `SuppressString`**. (`Mut*`/`Suppress*` exist only in `bool`/`int`.) The five top-level types are `ConstantString`, `IndependentString`, `SealString`, `PileStringImpl`, and the capstone interface `PileString`.

## Delta 2 — the String types extend the *Comparable* family, not the generic impls

In the bool family each `*Bool` extends the generic impl directly (`SealBool extends SealPile<Boolean>`). String inserts **one extra layer**: every `*String` extends the corresponding **`*Comparable`** type, which in turn extends the generic impl. So `String` IS-A `Comparable<String>` reactive value plus a (very thin) String veneer.

| `*String` type | extends | generic root | role |
|---|---|---|---|
| `PileStringImpl` | `PileComparableImpl<String>` | [`PileImpl`](../impl/PileImpl.md) | the default full reactive string; implements `PileString`. |
| `SealString` | `SealComparable<String>` | `SealPile` | sealable reactive string; the type `Piles.concatStrings`/`nullableWrapper`/`readOnlyWrapper` hand back. |
| `ConstantString` | `ConstantComparable<String>` | `Constant` | never-changing string; `setNull()` is a no-op returning `this` (silent-ignore idiom). |
| `IndependentString` | `IndependentComparable<String>` | `Independent` | always-valid, non-recomputing leaf; typed `setName`/`setNull`. |
| `PileString` (interface) | `ReadWriteListenDependencyString`, `PileComparable<String>` | `Pile` | capstone interface; static factory/wrapper hub. |

Each concrete class body is trivial: typed `setName`/`setNull` overrides for chaining, nothing more (read each — they are ~20 lines). For reactive semantics, read the generic doc; for the comparison surface, read the `specialized_Comparable` docs (sibling, pending). The `*String` layer adds almost nothing beyond what `*Comparable` already gives.

## Delta 3 — the 12 combination interfaces are nearly empty

Unlike the bool combinations (which carry the whole logic algebra), the String combinations are **pure assembly + a handful of conveniences**. Each unions its `*Comparable` counterpart re-typed to `String`; the notable additions:

- `ReadValueString`, `JustReadValueString`, `LastValueRemembererString` — **empty** (pure narrowing).
- `WriteValueString` — adds **`setEmpty()`** (`set("")`) and `setNull()`.
- `ReadListenValueString` / `ReadWriteListenValueString` — narrow the **buffer / validBuffer / rateLimited** family return types to `SealString`/`IndependentString` (same mechanical narrowing as bool).
- `ReadDependencyString` — `readOnly()` → `SealString`; `overridable()` → `PileStringImpl` (via `Piles.computeString`). No string operators.
- `ReadListenDependencyString` / `ReadWriteListenDependencyString` — `fallback(String)`, `setNull()`, and `nullableWrapper()` (delegates to `PileString.nullableWrapper`).
- `WriteElsewhereString` — **entirely commented out** (inert vestige, like `WriteElsewhereBool`).

There is **no `PileString.md` companion doc**, because there is no operator algebra to document — contrast `PileBool.md`. `PileString` is a factory/wrapper hub, covered below.

## Delta 4 — String-specific surface: concatenation lives elsewhere, plus null-vs-`""`

### Concatenation is NOT a method on the value types
There is no `concat(...)` on `PileString` or `ReadDependencyString`. The reactive concatenation operators live as static factories in [`Piles`](../impl/Piles/_index.md):

- `Piles.concatStrings(op1, op2)` — a sealed `SealString` = `String.valueOf(op1) + String.valueOf(op2)` of any two `ReadDependency<?>` (not just strings). Because it uses `String.valueOf`, a `null` operand concatenates as the literal `"null"`.
- `Piles.concatAny(preserveNull, args)` — concatenates the string representations of N values (constants or `ReadDependency`s), routing through the aggregation monoid `PileString.concatAggregation`. `preserveNull` only matters for the **single-element** case: a lone `null` yields a `null` string if `preserveNull`, else the constant `"null"` (`CONST_QUOTED_NULL`). For ≥2 args every part is stringified with `preserveNull=false`, so embedded nulls become `"null"`.

What `PileString` itself contributes to concatenation is just the plumbing: the `concatAggregation` `AggregationMonoid` (neutral = `EMPTY`; `apply` = `Piles.concatStrings`; `inject` maps non-String operands through the generic `mapToString(String::valueOf)`). `concatAny` calls it; users normally call `Piles.concat*`.

### Mapping *into* a reactive string
`mapTo*`-style reactive ops are **not** declared on the String types either — `mapToString(fn)` is a generic method on `pile.aspect.combinations.ReadDependency` (every reactive value can project to a `SealString`). There are **no `isEmpty`/`length`/substring-style reactive operators** anywhere in this package; string manipulation is done by mapping (`x.mapToString(...)`) or by `Piles.concat*`, not by a String operator algebra. (The only `substring`/`length` logic is the internal escaping inside `nullableWrapper`, not a public reactive op.)

### null vs the empty string
This is the one genuinely String-specific concern. `PileString` exposes the canonical constants and a null-encoding wrapper:

- `PileString.NULL` (= `Piles.constant((String)null)`), `PileString.EMPTY` (= `Piles.EMPTY_STRING`, a `ConstantString` of `""`), and `CONST_QUOTED_NULL` (constant `"null"`).
- `PileString.nullableWrapper(back)` / the instance `ReadWriteListenDependencyString.nullableWrapper()` — lets a reactive string that **cannot store `null`** (e.g. a GUI text field bound to a non-null backing) still represent `null`: it stores `null` as `""`, and escapes a real `""` or a string already starting with a space by **prepending a space** (stripped on read). A `SealString` whose seal writes the encoded form back to `back`.
- `concatStrings`/`concatAny` deliberately render `null` as `"null"` (via `String.valueOf`) unless `preserveNull` rescues a lone null.

## Companion docs
- None for the operator surface (there is no `PileString.md`). The combinations need no separate index — they add only `setEmpty`/buffer-narrowing/`fallback`/`nullableWrapper` as listed above. For everything structural, see the [bool exemplar](../specialized_bool/_index.md) and its [combinations index](../specialized_bool/combinations/_index.md).
- Concatenation factories: [`Piles`](../impl/Piles/_index.md) (`concatStrings`, `concatAny`, `EMPTY_STRING`).
- Aspect layer (where `mapToString`, buffers, `fallback` really live): [`../aspect/_index.md`](../aspect/_index.md).

## Caveats & gotchas
- **No operator algebra.** Don't look for `concat`/`isEmpty`/`length` on `PileString` — they aren't there. Use `Piles.concat*` for concatenation and `mapToString` (generic) for everything else.
- **Concat stringifies `null` as `"null"`** by default (it uses `String.valueOf`). Use `concatAny(true, …)` only to preserve a *single* lone null; it does not preserve nulls inside multi-arg concatenation.
- **`nullableWrapper` mutates the representation**: a real value of `""` or a leading-space string is stored space-prefixed in the backing value. Read it back through the wrapper, never the raw backing, or the escape leaks.
- `ConstantString.setNull()` silently returns `this` (constants are immutable) — idiomatic, not a bug.

## Tech debt / warts
- `WriteElsewhereString` is dead commented-out code (mirrors `WriteElsewhereBool`).
- `PileString.RightmostFulfilling.NOT_NULL` is typed as and constructs a **`LeftmostFulfilling`**, not a `RightmostFulfilling` — see SUSPECTED_BUGS. (The instance `apply` correctly prefers the right operand; only the unused static `NOT_NULL` constant is wrong.)
- Naming: `concatStrings`/`concatAny` live in `Piles`, away from the `String` types, so the type-specific operator is undiscoverable from the value's own API.
