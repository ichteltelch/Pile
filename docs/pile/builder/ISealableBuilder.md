# `pile.builder.ISealableBuilder`

The capability-builder interface that adds **seal configuration** to a builder: `seal(...)` overloads recorded now and applied to the value at `build` time, plus the privileged-setter accessors.

Source folder: `src`. File: `pile/builder/ISealableBuilder.java`.

`ISealableBuilder<Self extends ISealableBuilder<Self, V, E>, V extends `[`Sealable<E>`](../aspect/Sealable.md)`, E> extends `[`IBuilder<Self, V>`](IBuilder.md). It is one of the granular capability interfaces (alongside the doc-pending `ICorrigibleBuilder` / `IListenValueBuilder`) that the *target* builder interfaces assemble. Concretely it is inherited by [`IIndependentBuilder`](IIndependentBuilder.md) and `ISealPileBuilder` (see [builder index](_index.md)), so every `Independent` and `SealPile` builder is also a sealable builder.

For *what sealing is* — the four seal modes (throw / ignore / warn / redirect), the interceptor, redirection, the `makeSetter`-before-seal pattern — read the aspect doc [`Sealable`](../aspect/Sealable.md). This doc is a **delta over the javadoc** covering only the builder surface: which method records what, and how the two abstract bases consume the recorded request in `build`.

## What it's for

Sealing on the value itself is permanent and one-shot (see [`Sealable`](../aspect/Sealable.md)). The builder lets you express *"seal this value as the final step of construction"* fluently, so you can keep configuring the still-mutable value after calling `seal(...)`. **No overload seals eagerly** — each just records intent; the actual `value.seal(...)` fires last in `build` (in [`AbstractSealPileBuilder`](AbstractSealPileBuilder.md) for `SealPile`, in `AbstractIndependentBuilder` for `Independent`).

## The `seal(...)` family

Three method shapes, each with a no-`allowInvalidation` and an `allowInvalidation` variant. The **abstract** members are the two the bases actually implement; the rest are convenience `default`s that funnel into them.

| Method | Kind | Funnels into | Records |
|---|---|---|---|
| `seal` | abstract | — | default throw-mode seal |
| `seal(Consumer<? super E> interceptor)` | abstract | — | custom interceptor, `allowInvalidation=false` |
| `seal(Consumer, boolean allowInvalidation)` | abstract | — | custom interceptor + invalidation flag |
| `seal(Function<? super V, ? extends Consumer<? super E>> makeInterceptor)` | default | `seal(Consumer)` | applies the function to `valueBeingBuilt` first |
| `seal(Function, boolean)` | default | `seal(Consumer, boolean)` | same, with flag |
| `seal(BiConsumer<? super V, ? super E>)` | default | `seal(Consumer)` | binds the value being built as the BiConsumer's first arg |
| `seal(BiConsumer, boolean)` | default | `seal(Consumer, boolean)` | same, with flag |
| `sealWithSetter(BiConsumer<? super WriteValue<? super E>, ? super E>)` | default | `seal(Consumer)` | first arg is the privileged setter from `valueBeingBuilt.makeSetter` |

The `Function`/`BiConsumer` overloads exist so the interceptor can close over **the value being built** without you having a reference to it yet — they call `valueBeingBuilt` at *record* time to materialize the `Consumer` the bases store. `sealWithSetter` goes further and pre-fetches `valueBeingBuilt.makeSetter`, giving the interceptor a privileged write channel to redirect writes back into the same value (a write-through seal) — note this calls `makeSetter` eagerly, so it must run before the value is sealed.

## Setter accessors

- `makeSetter` — abstract; returns the privileged setter from the value being built (`Independent#makeSetter` / `SealPile#makeSetter`). Grab and retain this **before** `build`/`seal` to keep a write channel after sealing.
- `giveSetter(Consumer<? super Consumer<? super E>> out)` — default convenience: hands `makeSetter` to your `out` consumer (if non-null) and returns `self`, so you can capture the setter mid-chain without breaking the fluent call.

## How the bases apply the recorded seal in `build`

Both abstract bases keep a `boolean sealOnBuild` + `Consumer<? super E> interceptor` pair and implement the three abstract `seal` methods as plain field assignments; `build` seals last:

- [`AbstractSealPileBuilder`](AbstractSealPileBuilder.md) — also keeps an `allowInvalidation` field; `build` does `super.build` then `value.seal(interceptor, allowInvalidation)`. Honors all three recorded fields.
- `AbstractIndependentBuilder` — keeps only `sealOnBuild` + `interceptor`; `build` ends with `if(sealOnBuild) value.seal(interceptor, false)`. See the caveat below — it **drops** the `allowInvalidation` argument.

Because the seal is the final build step, everything else (bounds, correctors, brackets, remember-last-value, `init`) is configured on the still-mutable value first. This is the build-then-seal order `SealPile`'s runtime guards assume.

## Salient / surprising behavior

- **`seal(interceptor)` forces `allowInvalidation=false`** (the abstract one-arg form records the interceptor only; the bases never set the flag for it). To keep invalidation while supplying an interceptor you must use `seal(interceptor, true)` — and even then only the `SealPile` branch honors it (see below).
- **The `Function`/`BiConsumer`/`sealWithSetter` overloads call `valueBeingBuilt` eagerly** at record time, not at build time — so the interceptor closes over the value object, but its *behavior* still only takes effect when `build` seals.
- **`sealWithSetter` materializes a privileged setter immediately**. If you call it you have consumed `makeSetter` for the write-through interceptor; the value must still be unsealed at that point.

## Caveats & gotchas

- **`Independent` builders silently discard `allowInvalidation`.** `AbstractIndependentBuilder.seal(interceptor, allowInvalidation)` ignores the flag and `build` always seals with `false`. So `Piles.independent(x).seal(myInterceptor, /*allowInvalidation=*/true).build` produces a value on which `permaInvalidate` still throws — contrary to this interface's javadoc. `SealPile` builders honor the flag; `Independent` builders do not. (Reported in SUSPECTED_BUGS.)
- **Grab the setter before `build`.** Once `build` seals, `makeSetter` on the value throws; use `makeSetter`/`giveSetter(...)` on the builder beforehand and retain the result. (See [`Sealable` § the privileged bypass](../aspect/Sealable.md).)
- **`seal(...)` is record-only — order among other fluent calls is irrelevant** as long as `build` runs last. Don't expect `seal` to freeze the value mid-chain.
- **Re-calling `seal*` overwrites the prior request** (plain field assignment in both bases); the last call before `build` wins. The no-arg `seal` does *not* clear a previously-set `interceptor` — see [`AbstractSealPileBuilder` § tech debt](AbstractSealPileBuilder.md).

## Common tasks (how to…)

- **Make the built value read-only (throw on stray writes):** `…seal.build`.
- **Ignore stray writes instead of throwing:** `…seal(Functional.NOP).build`.
- **Redirect writes to another value:** `…seal(x -> other.set(transform(x))).build`, or use the value-aware `seal((v, x) -> …)` / `sealWithSetter((setter, x) -> …)` overloads when the interceptor needs the value or its privileged setter.
- **Keep a private write channel after sealing:** `b.giveSetter(setterHolder::set)…seal.build` (or `WriteValue<E> s = b.makeSetter`), then write via the retained setter.
- **Interceptor that needs the value being built:** `seal(v -> (x -> …use v…))` (`Function` overload) or `seal((v, x) -> …)` (`BiConsumer` overload).

## Tech debt / warts

- **`allowInvalidation` is dropped on the `Independent` branch** — the interface advertises a flag that `AbstractIndependentBuilder` does not implement (it lacks the field and hardcodes `false`). Asymmetric with `AbstractSealPileBuilder`. (SUSPECTED_BUGS.)
- **Empty/typo'd javadoc tags** on the interface: bare `@param <Self>`/`@param <V>`/`@param <E>` and several `@return` lines duplicated verbatim across overloads, consistent with the [project-wide note on unsystematic API](../../overview.md).
- **Javadoc says "Seal the `Independent`"** on every overload even though the interface is generic over any `Sealable` (and is also inherited by `SealPile` builders) — copy-paste from the `Independent` use site.

## Related

- [`Sealable`](../aspect/Sealable.md) — the seal model (modes, redirection, `makeSetter` pattern) these methods configure.
- [`AbstractSealPileBuilder`](AbstractSealPileBuilder.md) — applies the recorded seal for `SealPile` builders (honors `allowInvalidation`).
- [`IIndependentBuilder`](IIndependentBuilder.md) — one inheritor; its `AbstractIndependentBuilder` base applies the seal (drops `allowInvalidation`).
- [`IBuilder`](IBuilder.md) — root builder interface (`self`/`build`/`valueBeingBuilt`).
- [builder index](_index.md) · [overview](../../overview.md) · [concepts/](../../concepts/).
