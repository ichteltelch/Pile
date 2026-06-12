# `MutInt` — bare mutable `int` box; not a reactive graph node

`MutInt` is a plain mutable wrapper around a primitive `int` field (`val`). It is **not** a reactive value and does not participate in the dependency graph. It implements `JustReadValueInt` (the functional read-only snapshot interface) and `IntSupplier`. The integer analogue of [`MutRef`](../impl/MutRef.md) / `MutBool`.

Source folder: `src`. Package: `pile.specialized_int`.

Up: [int index](_index.md) · [overview](../../overview.md). Generic analogue: [`MutRef.md`](../impl/MutRef.md). Family exemplar: [`../specialized_bool/_index.md`](../specialized_bool/_index.md).

## What it is and isn't

`MutInt` is a **closure/out-parameter box** — a thin typed holder for passing a mutable integer through a lambda boundary or accumulating a value across calls. It has no listeners, no validity, no transactions, and no dependency tracking. It is always "valid" in the sense that its `val` field always holds a defined `int` (defaulting to `0`).

This is the only class in the int family that is **not** a `pile.aspect` type. `Mut*` exists for `bool` and `int` only, not for `String`/`Comparable`.

## API

| Member | Description |
|---|---|
| `int val` | The raw value (public field — read/write directly). |
| `MutInt()` | Default constructor; `val` initialized to `0`. |
| `MutInt(int val)` | Constructs with given initial value. |
| `MutInt set(int val)` | Sets `val`, returns `this` (fluent). |
| `MutInt set(MutInt o)` | Copies `o.val` into `this.val`, returns `this`. |
| `Integer get()` | Returns `val` boxed as `Integer`. Implements `JustReadValueInt`. |
| `int getAsInt()` | Returns `val` unboxed. Implements `IntSupplier`. |
| `String toString()` | Returns `"<val>"` — angle-bracket format. |

## `getAsInt()` — the primitive accessor note

**`getAsInt()` lives here, not on reactive int interfaces.** The reactive read hierarchy (`ReadValueInt`, `ReadDependencyInt`, etc.) returns `Integer` via the inherited `get()`. If you want an unboxed primitive read from a reactive int value, you must call `get()` and unbox manually (or use `threeWay`/null-safe pattern). The only unboxed primitive accessor in the int family is `MutInt.getAsInt()`.

## Common uses

- **Lambda out-parameter**: capture a `MutInt` in a closure to accumulate a counter or running total that the lambda writes back.
- **Suppressor tracking**: `SuppressInt.suppressBracket` uses a `MutInt openCount` internally to track how many times a bracket has been opened (avoids boxing).
- **Simple counter/index**: anywhere a plain `int` needs to be held by reference.

## Caveats & gotchas

- **Not thread-safe** — `val` is a plain non-volatile field; external synchronization is required for concurrent access.
- **Not reactive** — changes to `val` do not notify any listeners. If you need a reactive writable integer, use `IndependentInt` instead.
- **`get()` boxes on every call** — returns `new Integer(val)` (or via autoboxing cache for small values); prefer `getAsInt()` in tight loops.
