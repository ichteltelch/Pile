# `ReadDependencyComparable`

The substantive interface in this family: narrows [`ReadDependency`](../../aspect/combinations/ReadDependency.md) to `E extends Comparable<? super E>` and adds the full ordering-operator surface as default methods.

Source folder: `src`. Package: `pile.specialized_Comparable.combinations`.

Up: [combinations index](../_index.md) · [Comparable package index](../../specialized_Comparable/_index.md).

## What it is

`ReadDependencyComparable<E>` extends `ReadValueComparable<E>`, `Dependency`, and `ReadDependency<E>`. It is the interface at which the ordering surface enters the `*Comparable` hierarchy, and every downstream type (`ReadListenDependencyComparable`, `ReadWriteDependencyComparable`, `ReadWriteListenDependencyComparable`, and `PileComparable`) inherits all of it.

Every method declared here is a **default method that delegates** to a static factory on [`PileBool`](../../specialized_bool/PileBool.md) or [`PileInt`](../../specialized_int/PileInt.md). The interface itself adds no state.

## Ordering operators

All operators take a `Boolean nullIsLess` that controls null handling with three-valued semantics (see the [Comparable package index](../../specialized_Comparable/_index.md) for the full null-ordering convention). Every operator produces a **fresh sealed reactive node** of a different type family.

### Comparison → `SealBool` (delegates to `PileBool`)

Each has a reactive-operand form and a `*Const` form against a plain `E`:

| Method | Const form | Delegates to |
|---|---|---|
| `lessThan(op2, nullIsLess)` | `lessThanConst(op2, nullIsLess)` | `PileBool.lessThan` |
| `greaterThan(op2, nullIsLess)` | `greaterThanConst(op2, nullIsLess)` | `PileBool.greaterThan` |
| `lessThanOrEqual(op2, nullIsLess)` | `lessThanOrEqualConst(op2, nullIsLess)` | `PileBool.lessThanOrEqual` |
| `greaterThanOrEqual(op2, nullIsLess)` | `greaterThanOrEqualConst(op2, nullIsLess)` | `PileBool.greaterThanOrEqual` |

Return type: [`SealBool`](../../specialized_bool/SealBool.md).

### Comparison → `SealInt` (delegates to `PileInt`)

| Method | Const form | Delegates to |
|---|---|---|
| `compareTo(op2, nullIsLess)` | `compareToConst(op2, nullIsLess)` | `PileInt.comparison` |

Return type: [`SealInt`](../../specialized_int/SealInt.md). This is the reactive analogue of `Comparable.compareTo`, producing a reactive `int`.

### Wrappers

- `readOnly()` — wraps `this` in a [`SealComparable`](../SealComparable.md) via `Piles.makeReadOnlyWrapper`. Use to expose a settable value as read-only to downstream consumers.
- `overridable()` — builds a `PileComparableImpl` that recomputes from `this::get` and fires when `this` changes, named `<dependencyName()>*`. Use to give a read-only dependency a settable override layer.

## Caveats & gotchas

- **Results cross family boundaries.** The return types are `SealBool` and `SealInt`, not `SealComparable`. Once you call `lessThan(...)`, you are in the [`specialized_bool`](../../specialized_bool/combinations/_index.md) world and must use that family's API to continue chaining.
- **Three-valued `nullIsLess`.** `Boolean.TRUE` → null sorts less; `Boolean.FALSE` → null sorts greater; `null` → null operands produce a reactive `null` result (unknown ordering). Passing `null` is intentional and useful; accidentally auto-unboxing it causes `NullPointerException` inside the delegate.
- **No `compareTo` without `nullIsLess`.** There is no zero-argument or default-null-ordering overload; the caller must always decide how nulls compare. This is by design — silently picking a null policy is a source of hard-to-find bugs.
- **No write-through ordering.** There is no `ReadWriteDependencyComparable`-level method that writes back through a comparison (contrast `ReadWriteDependencyBool.invertIf`). Ordering is read-only-by-nature.
- **`compareTo` naming.** On the static side, `PileInt` names the method `comparison` (not `compareTo`) to avoid clashing with `Comparable.compareTo`; this interface bridges the naming by calling its method `compareTo` and delegating to `PileInt.comparison`.

## Common tasks

- Build a reactive boolean "is this value less than that?" → `a.lessThan(b, Boolean.TRUE)` (or `Boolean.FALSE` / `null` for null ordering).
- Compare against a fixed threshold → `a.lessThanConst(threshold, Boolean.FALSE)`.
- Get a reactive integer comparison result → `a.compareTo(b, nullIsLess)`.
- Expose a dependency as read-only (hide `set`) → `a.readOnly()` → `SealComparable`.
- Give a read-only dep an overridable layer → `a.overridable()` → `PileComparableImpl`.
