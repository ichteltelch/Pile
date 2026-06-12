# `FieldDouble`

GUI-bundle interface pairing a reactive writable double value with a display label and a step increment.

Source folder: `src`. Package: `pile.specialized_double`.

Up: [_index.md](_index.md) · [overview](../../overview.md).

## What it is

`FieldDouble` is a small interface — **not** a reactive impl, **not** related to `Piles.field`/`Piles.deref` despite the name. It groups three things a GUI spinner or stepper widget typically needs:

| Method | Type | Purpose |
|---|---|---|
| `value()` | `ReadWriteListenDependencyDouble` | the reactive double to display and edit |
| `getText()` | `String` | a display label / unit string for the field |
| `getIncrement()` | `double` | the step size (e.g. for up/down arrows) |

Think of it as a **view-model record for a numeric input field**: it carries no reactive behavior of its own and does not participate in the dependency graph. It merely packages three pre-built things so a widget can consume them together.

## Factory

`FieldDouble.make(Supplier<? extends String> text, double increment, ReadWriteListenDependencyDouble value)` returns an anonymous implementation. The `text` supplier is called on each `getText()` invocation, so the label can be dynamic (e.g. derived from locale or unit system) without the `FieldDouble` itself being reactive.

## Caveats & gotchas

- **Name confusion:** `FieldDouble` has nothing to do with `Piles.field` (which dereferences a reactive reference to yield a reactive value). The name "Field" here means "form field / UI field", not "dereferenced field". See [_index.md](_index.md) § Structural deltas vs the bool family.
- There is no reactive invalidation, no listener registration, and no dependency tracking on `FieldDouble` itself. All reactive behavior lives in the `value()` it wraps.
- The `text` supplier in `make` is called eagerly on every `getText()` — it is not memoized. If computing the label is expensive, wrap it externally.
- No `setName` / `setNull` — `FieldDouble` is not a `ReadValue` and does not participate in the pile naming/validity model.
