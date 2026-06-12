# `pile.aspect.bracket.HasBrackets`

The aspect for reactive values that can carry [`ValueBracket`](ValueBracket.md)s — register value/old-value/any-value brackets and bequeath the inheritable ones to derived values.

Source folder: `src`. File: `pile/aspect/bracket/HasBrackets.java`.

`HasBrackets<Self, E>` is a tiny capability interface (four methods, all heavily javadoc'd). [`ReadListenDependency`](../combinations/ReadListenDependency.md) extends it, so every full reactive value is a `HasBrackets`. The real machinery — opening/closing brackets, transferring them between current and old value — lives in [`AbstractReadListenDependency`](../../impl/AbstractReadListenDependency.md) (ARLD). See the package [_index.md](_index.md) for the bracket concept and the [overview](../../../overview.md) for where this sits.

## What it's for

A bracket is an effect that must endure for exactly as long as a reactive value holds one particular *plain* value (e.g. ref-counting, registering a listener, wiring a dependency). `HasBrackets` is the registration surface: you hand it a `ValueBracket`, tagged with *which* held-value slot it tracks.

## The three slots: value vs. old vs. any

A reactive value has two object slots a bracket can track: the **current value** (`__value`) and the **old value** (`__oldValue`, the pre-transaction snapshot). The three add-methods pick which slot(s) a bracket follows:

- **`_addValueBracket`** — opens when an object becomes the *current* value, closes when it stops being current. Tracks only the current slot.
- **`_addOldValueBracket`** — same, but for the *old* value slot.
- **`_addAnyValueBracket`** — opens when an object becomes current *or* old (whichever happens first, and only once even if it occupies both slots), and closes only when **both** slots have stopped pointing at it. This is the slot you want when you care that the object is held *at all*, regardless of which slot.

All three take `openNow`: if true and the relevant slot already holds a value, the bracket is opened immediately on registration (in `AbstractReadListenDependency`). All return nothing and are write-only (`_`-prefixed = internal-ish API).

The "any" semantics are what make this non-trivial: when current == old (the common steady state), an any-bracket is open *once*, and as the value transitions through a transaction the open instance is **transferred** between the current-tracking and old-tracking active lists rather than closed and reopened. That transfer logic is the bulk of the implementation (see below).

## `bequeathBrackets` — inheritance to derived values

`bequeathBrackets(openNow, v)` copies every registered bracket whose `isInheritable` is true onto another `HasBrackets v`, preserving the slot: inheritable value-brackets go to `v._addValueBracket`, old-brackets to `_addOldValueBracket`, any-brackets to `_addAnyValueBracket` (ARLD). Non-inheritable brackets are skipped. This is how a value derived from an existing one (a buffer, a transformed view, etc.) picks up the parent's inheritable effects. A bracket's inheritability is fixed at construction — most `ValueBracket` factories take an `inheritable` flag; the ref-count brackets (`REF_COUNT_BRACKET`, `COLLECTION_REF_COUNT_BRACKET`) are inheritable, most listener/dependency brackets are not (see [`ValueBracket`](ValueBracket.md)).

## Where ARLD implements them

[`AbstractReadListenDependency`](../../impl/AbstractReadListenDependency.md) is the sole real implementor (shared by `PileImpl`, `SealPile`, `Independent`, …). It keeps three registration lists (`brackets`, `oldBrackets`, `anyBrackets`) and three+one "active" lists (`activeBrackets`, `activeOldBrackets`, `activeAnyBrackets`, `activeAnyBracketsOnOld`) — the any-slot needs two active lists, one per object slot, to drive the transfer (ARLD). The lists are lazily allocated on first add and all access is under `mutex`.

The open/close engine:
- `openBrackets`/`closeBrackets` — drive the current-value brackets, and the any-brackets *when current ≠ old* (ARLD).
- `openOldBrackets`/`closeOldBrackets` — same for the old-value slot (ARLD).
- The **transfer** happens inside the close methods: when closing the current slot and `value == oldValue`, the open any-brackets are moved from `activeAnyBrackets` into `activeAnyBracketsOnOld` instead of being closed (ARLD); the old-side close does the mirror move (ARLD). This is what implements "close only when *both* slots release the object."

ARLD is documented in [AbstractReadListenDependency.md](../../impl/AbstractReadListenDependency.md) (see its Brackets section).

## Salient / surprising behavior

- **Brackets `open`/`close` run while `mutex` is held**, and ARLD asserts `ListenValue.DEFER.isDeferring` at open time. Do not do reentrant or destructive things (e.g. `destroy`) inside a bracket — defer to a `SequentialQueue` (the `ValueBracket.queued(...)` family exists for this). This is the single biggest gotcha; see [_index.md](_index.md).
- **Exceptions are swallowed.** Every `open`/`close` call in ARLD is wrapped in try/catch that logs at `WARNING` and continues (e.g. ARLD). A throwing bracket will not propagate; it just logs and the rest proceed.
- **`open` returns a "keep installed" boolean; `close` returns a "keep the value reference" boolean.** ARLD ORs the close results: if every active bracket returns false from `close`, the value reference may be nulled. The `ValueBracket.KEEP` bracket exists purely to force-retain a value (see [`ValueBracket`](ValueBracket.md)).
- **`openNow` on add only fires if the slot is currently valid** (`__valid` / `__oldValid`). Adding with `openNow=true` to an invalid value silently does not open — it will open on the next time a value becomes active.

## Caveats & gotchas

- **`Constant` no-ops all four methods**. A constant's value never changes, so a bracket would open once and never close; the class just drops every registration. This is idiomatic silent-ignore (consistent with constants ignoring writes), not a bug — but it means you cannot attach ref-counting or cleanup brackets to a `Constant` and expect them to fire.
- **Slot asymmetry is contractual, not enforced.** `_addValueBracket` will never see the old value, even if the same object later becomes old; use `_addAnyValueBracket` if you need that.
- The generic signatures differ between interface and impl: the interface declares `ValueBracket<? super E, ? super Self>`, ARLD declares `ValueBracket<? super E, ? super ReadListenDependency<? extends E>>`. `Self` is bound to `ReadListenDependency` for real values, so they line up, but the bracket's owner type you write against is `ReadListenDependency<? extends E>`.

## Common tasks

- **Ref-count a held value:** `_addAnyValueBracket(true, ValueBracket.REF_COUNT_BRACKET)` — inheritable, so it also flows to derived values via `bequeathBrackets`.
- **Run a side effect tied to the current value's lifetime:** build with `ValueBracket.make(inheritable, openFn, closeFn)` and `_addValueBracket`.
- **Make a derived value inherit a parent's effects:** ensure the bracket was created `inheritable=true`, then call `parent.bequeathBrackets(openNow, derived)`.
- **Avoid deadlock from a heavy bracket:** wrap it with `.queued(...)` / `.defer(...)` so open/close run off the `mutex` (see [`ValueBracket`](ValueBracket.md)).

## Tech debt / warts

- **Inconsistent `bequeathBrackets` arity vs. javadoc.** The interface javadoc references `bequeathBrackets(HasBrackets)` (one-arg) via its `@see`, but the actual method takes `(boolean openNow, HasBrackets v)`. The `ValueBracket.isInheritable` javadoc likewise `@see`s the one-arg form. Stale doc references; harmless.
- The `_add*` methods are write-only with no symmetric `_remove*` — once added (and the value not destroyed) a bracket stays registered for the object's life; obsolescence is signalled only by `open` returning false.
</content>
</invoke>
