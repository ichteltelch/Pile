# `PileString`

The capstone interface of the reactive String family — a factory and wrapper hub that adds String-specific constants, the `concatAggregation` monoid, and the `nullableWrapper` null-encoding utility on top of [`PileComparable<String>`](../specialized_Comparable/_index.md) and `ReadWriteListenDependencyString`.

Source folder: `src`. Package: `pile.specialized_String`.

## What it adds over the generic and Comparable layers

`PileString` contributes almost no operator algebra (contrast `PileBool`). Its additions are:

### Constants

- **`NULL`** — `ConstantString` holding `null` (= `Piles.constant((String)null)`).
- **`EMPTY`** — `ConstantString` holding `""` (= `Piles.EMPTY_STRING`).
- **`CONST_QUOTED_NULL`** — `ReadListenDependencyString` holding the literal string `"null"`.

### Builders / factory methods

- **`rb()`** — `PileBuilder<PileStringImpl, String>` pre-configured with `Comparator.naturalOrder()`.
- **`sb()`** — `SealPileBuilder<SealString, String>` pre-configured with `Comparator.naturalOrder()`.
- **`ib()`** / **`ib(String init)`** — `IndependentBuilder<IndependentString, String>` with natural ordering; `ib()` uses `null` as the initial value.

### Read-only wrappers

- **`readOnlyWrapper(in)`** — always creates a new `SealString` sealed over `in`.
- **`readOnlyWrapperIdempotent(in)`** — returns `in` itself when it is already a default-sealed `SealString` or a `ConstantString`; otherwise delegates to `readOnlyWrapper`.

### `concatAggregation` monoid

An `AggregationMonoid<Object, ReadListenDependencyString>` used by `Piles.concatAny` as the fold structure for multi-value concatenation:
- neutral element = `EMPTY`;
- `apply(op1, op2)` = `Piles.concatStrings(op1, op2)` (a `SealString`);
- `inject(o)` = wraps non-`String` operands via `mapToString(String::valueOf)` before handing to `readOnlyWrapperIdempotent`.

Users normally call `Piles.concatStrings` / `Piles.concatAny` directly. `concatAggregation` is the plumbing; see [`Piles/_index.md`](../impl/Piles/_index.md) for the concat factories and null-stringification rules.

### `nullableWrapper(back)`

A `SealString` that lets a non-null-capable backing `ReadWriteListenDependency<String>` (e.g. a GUI text-field binding) represent `null`. Encoding: `null` → `""` in `back`; `""` or a leading-space string → `" " + value` in `back`. The seal writes encoded form back; the recompute strips the leading space (or maps `""` to `null`). **Always read through the wrapper, never the raw backing** — the space-prefix escape leaks if read directly.

The instance method `ReadWriteListenDependencyString.nullableWrapper()` delegates to this static factory.

### `LeftmostFulfilling` / `RightmostFulfilling`

Nested `SidemostFulfilling<String, SealString>` specializations for picking the leftmost or rightmost operand that satisfies a predicate (e.g. `IS_NOT_NULL`). Each returns a `SealString`. The static `NOT_NULL` convenience constant is pre-configured with `IS_NOT_NULL` predicate and `null` as the fallback value when neither operand qualifies.

## Caveats & gotchas

- **No concat method on the interface.** Call `Piles.concatStrings(a, b)` or `Piles.concatAny(preserveNull, args...)`. See [Piles/_index.md](../impl/Piles/_index.md).
- **`RightmostFulfilling.NOT_NULL` is typed as `LeftmostFulfilling`.** The field `RightmostFulfilling.NOT_NULL` is declared and constructed as `LeftmostFulfilling` — a copy-paste error. The instance `apply` in `RightmostFulfilling` correctly prefers the right operand; only the static constant is wrong (it behaves as leftmost if used). See SUSPECTED_BUGS.
- `nullableWrapper` uses `String.charAt(0)` without a length check only after checking `length()==0` in the `""` branch — the logic is safe, but read carefully: the non-null, non-empty branch checks `charAt(0)==' '` to detect the escape prefix.
- `CONST_QUOTED_NULL` is typed as `ReadListenDependencyString` (not `ConstantString`), unlike `NULL` and `EMPTY`.

## Tech debt / warts

- `RightmostFulfilling.NOT_NULL` is a `LeftmostFulfilling` instance — see SUSPECTED_BUGS.
- The commented-out lines inside `concatAggregation.apply` (alternative `mapToString` paths) are dead code.

See also: [_index.md](_index.md) · [specialized_Comparable/_index.md](../specialized_Comparable/_index.md) · [Piles/_index.md](../impl/Piles/_index.md) · [overview](../../overview.md).
