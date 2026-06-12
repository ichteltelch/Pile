# `pile.aspect.LastValueRememberSuppressible`

The **super-interface for objects whose "remember the last value" behavior can be temporarily suppressed** via a released [`Suppressor`](suppress/Suppressor.md) — designed so that many such objects can be batch-suppressed through one method handle.

Source folder: `src`. File: `pile/aspect/LastValueRememberSuppressible.java`.

This is the **suppression** half of the remember-last-value feature. The behavioral half is [`RemembersLastValue`](RemembersLastValue.md) (which `extends LastValueRememberSuppressible.Single`); the storage strategy is [`LastValueRememberer`](LastValueRememberer.md). See the [package index](_index.md) and [overview](../../overview.md).

## What it is for

The interface exists primarily so that **several remember-capable values can be put into an array or `Collection` and suppressed together**. It supplies static method-handle constants that tell `Suppressor`'s batch machinery how to derive a `Suppressor` from each object, instead of every call site re-spelling the lambda. By itself the interface declares only the *control surface* (two methods); the counting/gating lives in the implementor (canonically `pile.impl.Independent`, via `RemembersLastValue`).

You rarely implement this interface directly — you implement one of the nested sub-interfaces (`Single`/`Multi`/`None`), or you inherit it through `RemembersLastValue`.

## The two core methods

Both are declared abstract on the interface:

- `Suppressor suppressRememberLastValues` — make a single `Suppressor` that suppresses this object's remember-behavior until released.
- `SuppressMany suppressRememberLastValues(SuppressMany s)` — **add** the suppressor(s) for this object to a caller-supplied [`SuppressMany`](suppress/Suppressor.md) and return it.

The split exists because some objects produce one suppressor and some produce several; the two nested mix-ins each implement one method in terms of the other (below).

> Caveat — `s` must be non-null. The javadoc explicitly forbids allocating a fresh `SuppressMany` when `null` is passed: on an exception the straightforward code path would leak the `Suppressor`, and the author decided requiring non-null is cheaper than writing leak-safe allocation. Callers pass something non-null.

## Nested types — the implementation menu

You pick a nested sub-interface based on how many suppressors your object yields. Capture this structure: **`RemembersLastValue extends Single`** is the main real-world consumer.

| Nested type | Implements (default) | In terms of | Use when |
|---|---|---|---|
| `Single` | `suppressRememberLastValues(SuppressMany)` | calls your `suppressRememberLastValues`, then `s.makePlaceFor1.add(...)` | your object produces **one** suppressor, or you want them organized hierarchically. |
| `Multi` | `suppressRememberLastValues` | builds a fresh `SuppressMany`, calls your `suppressRememberLastValues(SuppressMany)` into it | your object produces **multiple** suppressors. |
| `None` | **both** | `suppressRememberLastValues` → `Suppressor.NOP`; the `SuppressMany` form returns `s` untouched | a superclass forces you to implement the interface but there's nothing to suppress. (`extends Multi`.) |

So an implementor overrides **exactly one** of the two methods (`Single` → you write the no-arg one; `Multi` → you write the `SuppressMany` one), or neither (`None`).

`Multi.suppressRememberLastValues` is leak-safe: it releases the partially-built `SuppressMany` in a `finally` if `suppressRememberLastValues(SuppressMany)` throws — the safety the abstract method's javadoc warns callers to preserve.

`Single`'s default calls `SuppressMany.makePlaceFor1`, which reserves capacity for exactly one child before `add` — the "organized hierarchically" wording: the single suppressor becomes its own node rather than being flattened.

## The static method handles — the point of the interface

Three constants adapt the instance methods to `Suppressor`'s batch API ([`SuppressMany`](suppress/Suppressor.md) / `SuppressionSwitcher`):

- `SUPPRESS_LAST_VALUE_REMEMBERING : Function<LVRS, Suppressor>` — bound to `suppressRememberLastValues`. Feed to `Suppressor.many(Function, Collection)` / `many(Function, Object...)` to suppress a whole collection at once.
- `SUPPRESS_LAST_VALUE_REMEMBERING_ADD : BiConsumer<LVRS, SuppressMany>` — bound to `suppressRememberLastValues(SuppressMany)`. Feed to `SuppressMany.add(BiConsumer, ...)` / `more(...)`.
- `SUPPRESS_LAST_VALUE_REMEMBERING_COLLECTION : BiConsumer<Iterable<? extends LVRS>, SuppressMany>` — `Suppressor.lift(...)` applied to the `_ADD` handle, so a single call adds the suppressors of an **entire iterable** to a `SuppressMany`.

(The javadoc on `_COLLECTION` says "collection of `AutoValidationSuppressible`s" — that is a **copy-paste slip**; the element type is `LastValueRememberSuppressible`, as the field's declared generics show. See Tech debt.)

## Salient / surprising behavior

- **The interface holds no state and does no counting.** "Suppression is reference-counted / re-entrant" is true of the *implementor* (`Independent`'s `storingSuppressors`), not of this interface — see the override-map and reference-counting discussion in [`RemembersLastValue`](RemembersLastValue.md).
- **Plural method name, singular concept.** `RemembersLastValue` spells its canonical method `suppressRememberLastValue` (singular) and provides a `default suppressRememberLastValues` (plural) only to satisfy *this* super-interface, whose constants are spelled in the plural. The plural delegates to the singular. Don't be misled into thinking the plural produces multiple suppressors for an ordinary value — it doesn't.
- **`None` returns `NOP`/untouched `s`** — a genuine no-op aspect, the idiomatic stub when an inheritance hierarchy demands the interface but the value cannot remember anything.

## Caveats & gotchas

- Pass a **non-null** `SuppressMany` to `suppressRememberLastValues(SuppressMany)` (leak avoidance, above).
- Implement **one** nested sub-interface; implementing both `Single` and `Multi` makes both defaults mutually recursive (each calls the other) — a `StackOverflowError` waiting to happen. Pick the one matching your suppressor count.
- Always release whatever `Suppressor` (or `SuppressMany`) you obtain — otherwise remembering stays suppressed for the life of the scope. `SuppressMany` is itself a `Suppressor`, so a single `release` releases all collected children.

## Common tasks (how to…)

- **Suppress remembering for one value:** prefer `RemembersLastValue.suppressRememberLastValue` directly (see that doc); `suppressRememberLastValues` is the equivalent plural alias.
- **Suppress remembering across many values at once:**
  ```java
  Suppressor s = Suppressor.many(
      LastValueRememberSuppressible.SUPPRESS_LAST_VALUE_REMEMBERING, values);
  try { /* programmatic writes to all of values */ } finally { s.release; }
  ```
  or accumulate into an existing batch with `SUPPRESS_LAST_VALUE_REMEMBERING_COLLECTION` / `SuppressMany.add(SUPPRESS_LAST_VALUE_REMEMBERING_ADD, values)`.
- **Stub the aspect when there's nothing to remember:** `implements LastValueRememberSuppressible.None`.
- **Implement it for a real value:** almost always via `implements RemembersLastValue` (which is a `Single`); only implement `Multi` if your object owns several independently-suppressible remember behaviors.

## Tech debt / warts

- The `SUPPRESS_LAST_VALUE_REMEMBERING_COLLECTION` javadoc mis-names the element type as `AutoValidationSuppressible` — a copy-paste artifact from the sibling [`AutoValidationSuppressible`](AutoValidationSuppressible.md) aspect, which has the same method-handle pattern. The generics are correct; only the prose is wrong.
- Abstract `@return` tags are empty, consistent with the project-wide note that some API documentation is thin (see [overview § caveats](../../overview.md)).
- Mutually-recursive defaults across `Single`/`Multi` are unguarded — the contract "implement exactly one" is documentation-only.

## Related

- [`RemembersLastValue`](RemembersLastValue.md) — the behavior aspect; **`extends LastValueRememberSuppressible.Single`**. Your nested `Single` supplies its `suppressRememberLastValues(SuppressMany)`; `RemembersLastValue` supplies the no-arg suppressor via its own `suppressRememberLastValue`.
- [`LastValueRememberer`](LastValueRememberer.md) — the storage strategy (doc-pending).
- [`suppress/Suppressor`](suppress/Suppressor.md) — `Suppressor`, `SuppressMany`, and the `many(...)`/`lift(...)` batch API these handles plug into (doc-pending).
- [`AutoValidationSuppressible`](AutoValidationSuppressible.md) — the parallel suppress-an-aspect interface (same method-handle idiom).
- [package index](_index.md) · [overview](../../overview.md)
