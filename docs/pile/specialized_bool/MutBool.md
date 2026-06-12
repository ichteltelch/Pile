# `pile.specialized_bool.MutBool`

A bare mutable `boolean` box — a closure/out-parameter cell, **not** a reactive graph node.

Source folder: `src`. File: `pile/specialized_bool/MutBool.java`.

Up: [bool index](_index.md) · [overview](../../overview.md). Boolean analogue of: [`../impl/MutRef.md`](../impl/MutRef.md).

## What it is

`MutBool` is a `final` class that holds a single primitive `boolean` field `val`. It implements `JustReadValueBool` (boolean-typed always-valid read), `BooleanSupplier`, and `Prosumer<Boolean>` (consumer + supplier). It is **not** a `Pile`, not a `Dependency`, not a listener target — no reactive machinery whatsoever.

Use it wherever you need a mutable boolean box for a closure capture, an out-parameter, or any context where the value must be mutable but **must not touch the reactive graph**.

## The field and its accessors

The entire state is the **public** field `val` (always primitive, never null):

| Member | Behavior |
|---|---|
| `val` | The raw `boolean` field. Read/write directly for maximum locality. |
| `get()` | Returns `Boolean.valueOf(val)` (boxed). |
| `getAsBoolean()` | Returns `val` unboxed — the `BooleanSupplier` entry point. |
| `set(boolean val)` | Assigns `this.val = val`, returns `this`. |
| `set(MutBool o)` | Copies `o.val` into `this.val`, returns `this`. |
| `setTrue()` | `this.val = true` (no return). |
| `setFalse()` | `this.val = false` (no return). |
| `accept(Boolean t)` | `val = t` — the `Consumer<Boolean>` / `Prosumer` entry. **Throws `NullPointerException`** if `t` is `null` (unboxing). |
| `toString()` | `"<" + val + ">"` |

## Differences from `MutRef<Boolean>`

`MutRef<Boolean>` implements the full `ReadWriteValue<Boolean>` / `JustReadValue<Boolean>` contract (with no-op transaction and corrector overrides). `MutBool` is lighter:

- It is a **primitive** field (`boolean val`), not a reference field — no boxing per read.
- It implements only `JustReadValueBool` + `BooleanSupplier` + `Prosumer<Boolean>` — a smaller interface footprint.
- It has convenience `setTrue()` / `setFalse()` and a copy-from-other `set(MutBool)`.
- `accept(Boolean)` throws `NullPointerException` on `null` (the javadoc calls this out explicitly); `MutRef.set(T)` takes any reference.

## Caveats & gotchas

- **`accept(Boolean t)` unboxes eagerly** — passing `null` throws `NullPointerException`. If `null` is a valid state you need, use `MutRef<Boolean>` instead.
- **Not reactive.** Writing `val` or calling any setter fires no listeners and invalidates nothing. If you expect a downstream `Pile` to update, you picked the wrong type.
- **Public field** — `val` can be mutated by any code that holds the reference, with no encapsulation.
- The `val` field is never `null` (primitive), so `get()` always returns a non-null `Boolean` — unlike most `Pile` types where `null` is a valid three-valued state.
- The class javadoc says the no-arg constructor initialises with "0" (copied from the `MutInt` sibling) — it actually initialises `val` to `false`.

## Common tasks

- **Mutable boolean for a closure:** `MutBool b = new MutBool(); lambda = () -> b.val;` then `b.setTrue()` outside.
- **Out-parameter for a boolean result:** pass `MutBool` to a method; it reads `b.val` after the call.
- **Use as `BooleanSupplier`:** pass directly anywhere a `BooleanSupplier` is expected.
- **Use as `Consumer<Boolean>`:** pass `b::accept` — but guard against `null` before calling.

## Tech debt / warts

- The no-arg constructor javadoc says `initial value of 0` — a copy-paste from the `MutInt` family; the actual default is `false`.

## Related

- [`../impl/MutRef.md`](../impl/MutRef.md) — the generic mutable box; use for `null`-capable or reference-typed values.
- [`_index.md`](_index.md) — family overview; `MutBool` and `SuppressBool` are the two non-generic-impl bool types.
- [`combinations/_index.md`](combinations/_index.md) — `JustReadValueBool` (the combination interface `MutBool` implements).
