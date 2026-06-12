# `Bijection`

An invertible `Function` — a forward + backward function pair, used as a codec / two-way transform.

Source folder: `src`. Package: `pile.utils`.

`Bijection<From, To>` extends `java.util.function.Function<From, To>`, so it *is* the forward function (`apply`), plus an `applyInverse` that maps back. It is the small building block Pile uses wherever a value needs a reversible encode/decode: a `Bijection<T, String>` codecs a typed value to and from preferences storage (see [`PreferencesBackedValue`](../interop/preferences/PreferencesBackedValue.md)), and the specialized number types build reversible transform operators on it (`PileDouble`, `PileInt`).

Up: [overview](../../overview.md) · package: [utils index](_index.md) · sibling: [`Functional`](Functional.md) · related: [transform aspect](../aspect/transform/_index.md).

## The two directions

- `apply(From)` → `To` — the forward map (inherited `Function` contract).
- `applyInverse(To)` → `From` — the backward map.

These must be true inverses of one another: `applyInverse(apply(x))` should equal `x` and `apply(applyInverse(y))` should equal `y`. Nothing enforces this; it is a caller contract (see *Contract* below). When a `Bijection` is used as a codec, "forward" is conventionally encode and "inverse" is decode (e.g. value→String and String→value).

## `inverse()` — swapping the directions

`inverse()` returns *another* `Bijection` with the two directions flipped (its `apply` is your `applyInverse` and vice versa). The default implementation wraps `this` in the static nested class `InverseBijection`, whose `apply`/`applyInverse` simply delegate to the wrapped bijection's opposite method. `InverseBijection.inverse()` short-circuits back to the original wrapped instance rather than double-wrapping, so `x.inverse().inverse()` gives back the *same object* `x` only along the `InverseBijection` path — see the caveat below.

Involutions (functions that are their own inverse) are told by the javadoc to override `inverse()` to return `this`; `involution(...)` does exactly that.

## Factories

- `Bijection.define(Function to, Function fro)` — build a bijection from two separate functions asserted to be inverses. Both args are null-checked (`Objects.requireNonNull`). The returned anonymous bijection lazily memoises its `inverse()` (caches an `InverseBijection` on first call in the field `inverse`).
- `Bijection.involution(Function<T,T> f)` — build a `Bijection<T,T>` from a single self-inverse function: both `apply` and `applyInverse` call `f`, and `inverse()` returns `this`. Use for transforms like negation, bitwise-NOT, or `1 - x` where applying twice is the identity. Note: `f` here is **not** null-checked (unlike `define`).
- `new InverseBijection<>(b)` — rarely needed directly; `inverse()` produces these for you. Its constructor null-checks its argument.

There is no `identity()` factory and no general `compose`/`andThen` override for `Bijection` in this file — composition would itself need to compose both directions, and that is not provided here. Build identity with `involution(x -> x)` if needed.

## Contract (must be a true inverse)

The whole type is meaningful only if forward and backward genuinely invert each other over the domain in use. `define` cannot check this — it trusts the caller. A non-invertible pair will silently produce wrong round-trips (e.g. a codec that decodes to a different value than was encoded). This is the one real gotcha: the type *name* promises invertibility, but the *code* only stores two arbitrary functions.

## Caveats & gotchas

- **No invertibility check.** `define` accepts any two functions; correctness is on the caller (see *Contract*).
- **`involution` skips the null check** that `define` and `InverseBijection` perform on their function arguments — passing `null` defers the NPE to first use.
- **`inverse().inverse()` identity is only guaranteed via `InverseBijection`.** For a bijection produced by `define`, calling `inverse()` yields an `InverseBijection`, whose own `inverse()` returns the original — good. But a hand-rolled `Bijection` that does *not* override `inverse()` returns a fresh `InverseBijection` each call, so `b.inverse() != b.inverse()` (new wrapper per call). Memoise in your own override if identity matters.
- **`null` handling is whatever the wrapped functions do** — the bijection adds no null tolerance of its own.

## Common tasks

- **Make a codec** (value ⇄ String for storage): `Bijection.define(v -> encode(v), s -> decode(s))`, then hand it to a value that needs encode/decode such as [`PreferencesBackedValue`](../interop/preferences/PreferencesBackedValue.md).
- **Reuse a transform in both directions**: keep one `Bijection` and call `.inverse()` instead of writing the reverse function twice.
- **Self-inverse transform** (negate, flip, reverse): `Bijection.involution(f)`.
- **Identity**: `Bijection.involution(x -> x)`.

## Tech debt / warts

- Inconsistent null-checking across the factories (`involution` is the odd one out).
- No `identity()` / `compose()` despite both being natural for a bijection toolkit — callers improvise.
- The interface carries a *nested implementation class* (`InverseBijection`) and two static factories, mixing contract and implementation in one file; fine at this size but not separated.
