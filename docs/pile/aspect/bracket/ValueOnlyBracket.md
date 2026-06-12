# `ValueOnlyBracket`

A [`ValueBracket`](ValueBracket.md) variant that cares only about the held value, not the object holding it — and the one bracket kind a sealed value still accepts.

Source folder: `src` · package `pile.aspect.bracket`.

Up: [bracket index](_index.md) · [aspect index](../_index.md) · [Pile overview](../../../overview.md).

## What it's for

A plain `ValueBracket<E, O>` is opened/closed with two arguments: the `value` (`E`) and the `owner` (`O`) that holds it. A `ValueOnlyBracket<V>` is the special case where the bracket's effect depends only on the value and ignores the owner. It is declared as `ValueOnlyBracket<V> extends ValueBracket<V, Object>` — the owner type is pinned to `Object` and, by convention, never read inside `open`/`close`.

Concretely the implementations pass `Object owner` and don't touch it (see the `openOnly`/`closeOnly`/`make` factories on `ValueBracket` that return `ValueOnlyBracket`, e.g. `ValueBracket.java`, , ). The interface itself adds no `open`/`close` — it only **narrows the return types** of the decorator/factory chain.

## How it differs from a plain `ValueBracket`

- **Owner-agnostic.** The contract is "do something for as long as *this value* is held," with no reference to who holds it. A plain `ValueBracket` can key its behavior on the owner (e.g. `ownerValueListenerBracket`, which registers a listener *on the owner* — that one is deliberately a full `ValueBracket<Object, ListenValue>`, not value-only).
- **Single-argument fluent API.** The decorator methods are overridden to take `Consumer`/`Predicate` (value only) instead of `BiConsumer`/`BiPredicate` (value + owner), and to return `ValueOnlyBracket<V>` so the value-only-ness is preserved through chaining: `filtered`, `nopOnNull(Open|Close)`, `nonreentrant`, `queued(...)`, `beforeOpening`/`beforeClosing`, `defer`, `detectStuck`. Each wraps the corresponding `XxxBracket.ValueOnly<>` inner subclass.
- **`filtersFirst` / `dontDetectStuck` are identity no-ops** here — they `return this`. (`filtered` already applies before the wrapped bracket for these, so there is nothing to reorder.)

There is **no separate "old-value-only" type.** "Value-only" describes the *open/close signature* (owner ignored), not which of the three bracket slots (current / old / any value) it is installed into. The same `ValueOnlyBracket` can be added via `_addValueBracket`, `_addOldValueBracket`, or `_addAnyValueBracket` on a [`HasBrackets`](HasBrackets.md). The task framing "applies only to the current value (not the old value)" is true of its *typical use* but is not enforced by the type; what the type guarantees is owner-independence.

## The sealing carve-out (`SealPile`)

[`SealPile`](../../impl/SealPile.md) normally rejects any structural change once sealed. For brackets it makes one exception: a sealed `SealPile` still accepts a new bracket **iff it is a `ValueOnlyBracket`**. All three add-points carry the same guard:

```
if (sealed != null && !(b instanceof ValueOnlyBracket<?>))
    throw new IllegalStateException("Cannot change bracketing of a sealed SealableValue");
```

— `SealPile.java` (`_addValueBracket`),  (`_addOldValueBracket`),  (`_addAnyValueBracket`).

Rationale: a value-only bracket can't reach back into the sealed value's *structure* (it never touches the owner), so installing one after sealing is considered harmless — e.g. attaching a value-scoped listener or a reference-count bracket to an already-sealed value. The carve-out is keyed on the **runtime type** of the bracket, not on which `_add*` method is called: even `_addOldValueBracket`/`_addAnyValueBracket` admit a `ValueOnlyBracket` post-seal, while a full `ValueBracket` is rejected by all three.

## Notable factories that return `ValueOnlyBracket`

Most ready-made brackets on `ValueBracket` are value-only (and therefore seal-survivable). Worth knowing:

- `ValueBracket.REF_COUNT_BRACKET` / `COLLECTION_REF_COUNT_BRACKET` — inc/dec a `ReferenceCounted` value (or each element) while held; `nopOnNull`.
- `ValueBracket.KEEP` — pins the value reference (its `close` returns `true`) so a recomputation can restore it via `getAsync`.
- `valueListenerBracket`, `dependencyBracket*`, `dependerBracket*`, `revalidateBracket` — all return `ValueOnlyBracket`.

## Caveats & gotchas

- **Owner is `Object` and must be ignored.** Don't write a `ValueOnlyBracket` whose `open`/`close` casts or inspects the owner — the whole point (and the seal carve-out's safety argument) is that it doesn't. If you need the owner, use a full `ValueBracket`.
- **`open`/`close` run under the value's `mutex`.** Same constraint as every bracket: don't do reentrant/destructive work inline; defer via `queued(...)` / `defer(...)`. See the [bracket index](_index.md) caveat.
- **Value-only ≠ current-value-only.** The name describes argument shape, not bracket slot; the type doesn't stop you from installing one as an old-value or any-value bracket.
- **`queued(SequentialQueue)` semantics:** the param-light `queued(...)` overloads document themselves as "filters out null values," but the implementation passes `null` filters — null-filtering is *not* applied unless you chain `nopOnNull`. (This javadoc/behavior mismatch is inherited verbatim from `ValueBracket`.)

## Tech debt / warts

- Heavy overload duplication: the `queued(...)` family is copy-pasted from `ValueBracket` with the bi-predicate arguments stripped; the stale "filters out null values" javadoc rode along (see above).
- The owner-ignoring contract is by convention only — the compiler can't enforce that a `ValueOnlyBracket` never reads its `Object` owner, so the `SealPile` carve-out's safety rests on authors honoring it.
