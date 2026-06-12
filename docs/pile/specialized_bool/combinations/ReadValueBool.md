# `ReadValueBool` — primitive `boolean` read surface over `ReadValue<Boolean>`

Source folder: `src`. Package: `pile.specialized_bool.combinations`.

Narrows [`ReadValue`](../../aspect/ReadValue.md) to `Boolean` and adds primitive `boolean` accessors; also implements `BooleanSupplier` (`getAsBoolean()` = `isTrue()`). This is the root of the boolean read surface — every richer bool interface inherits these accessors.

## New members (all default/static)

- `isTrue()` / `isFalse()` — `Boolean.TRUE`/`FALSE` equality on `get()`; both return `false` when the value is `null` (three-valued; no NPE).
- `getAsBoolean()` — `BooleanSupplier` implementation; identical to `isTrue()`.
- `threeWay(ifTrue, ifFalse, ifNull)` — non-reactive three-way switch on the current snapshot; the reactive counterpart is `chooseConst(...)` on [`ReadDependencyBool`](ReadDependencyBool.md).
- Static `isTrue(Supplier)` / `isFalse(Supplier)` — same null-safe tests against any `Supplier<? extends Boolean>`.

## Caveats

`isTrue()` and `isFalse()` are **not complementary**: both are `false` when the value is `null`. Use `threeWay` when genuine three-way logic is needed.

Up: [combinations index](_index.md) · [bool package index](../_index.md) · [overview](../../../overview.md).
