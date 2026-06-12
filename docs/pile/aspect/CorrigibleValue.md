# `pile.aspect.CorrigibleValue`

Source folder: `src`. File: `pile/aspect/CorrigibleValue.java`.

The **aspect interface a reactive value implements when incoming writes pass through a chain of *correctors*** — small functions that can normalize the written value, replace it, or *veto* the write by throwing a [`VetoException`](VetoException.md). It is the mechanism behind the "the value you `set` may not be the value that lands" behavior of [`WriteValue#set`](WriteValue.md).

See the [overview](../../overview.md) for where this sits in the architecture. Correction is invoked on the **`set` path** (and on recomputation fulfillment) — see [`WriteValue` § ways a write can be refused/ignored/redirected](WriteValue.md).

## What it is for

The interface is tiny — two methods:

- `E applyCorrection(E value) throws VetoException` — run the installed corrector chain over `value` and return the **corrected** value that will actually be stored.
- `void _addCorrector(Function<? super E, ? extends E> corrector)` — install one elementary corrector step.

A *corrector* is a `Function<? super E, ? extends E>` that, given a candidate value, returns either the same value (possibly mutated), a different value, or throws a `VetoException` to refuse the write.

## Normalize vs. veto

A corrector has two ways to influence a write:

- **Normalize / replace** — return a value. `applyCorrection` feeds the returned value into the next corrector, and the final result is what gets stored. This is how clamping, rounding, defaulting, or (e.g.) unit-vector normalization would be expressed: the corrector returns the adjusted value rather than the raw one.
- **Veto** — throw a `VetoException` ([`VetoException`](VetoException.md), `VetoException.java`). The write is abandoned; nothing is stored. Per the Javadoc, a `VetoException` is **not logged**, whereas any *other* exception out of a corrector **should be logged and the change rejected**. If `VetoException.revalidate` is set and the context allows it, the value should react by recomputing itself instead.

### Concrete examples in the codebase

There is no unit-vector example in this unit; the real correctors shipped with Pile are bounds and null-guards installed by the builders:

- `ICorrigibleBuilder.neverNull` installs a corrector that throws `new VetoException("This value may not be set to null!")` when the candidate is `null` — a pure veto.
- `ICorrigibleBuilder.applyBounds(...)` installs up-to-three correctors: one that returns the upper bound when the value exceeds it, one that returns the lower bound when below it (both normalize-by-clamping), and one that vetoes when the bounds are inconsistent or a bound is currently invalid. A bound that cannot be read throws `VetoException("applyCorrection veto: …", e)`.

## Ordering / composition of multiple correctors

`_addCorrector` appends; **correctors run in the order they were added**. The implementation iterates the list and threads the value through each in turn, so each corrector sees the output of the previous one:

```
for(Function c: correctors) value = c.apply(value);
```

The corrector list is **lazily allocated** and only synchronized on itself during iteration; if no corrector was ever added, `applyCorrection` returns its argument unchanged. A veto thrown by any corrector aborts the chain immediately (it propagates out of `applyCorrection`), so later correctors never run for a vetoed write.

## How `set` consumes correction

Concrete piles call `applyCorrection` at the top of `set`, *before* opening the transaction or comparing for equivalence (`PileImpl.java`, and `Independent.java`):

- A returned value replaces the argument and continues down the normal `set` path.
- A `VetoException` is caught: if `revalidate` is set, `revalidate` is called; otherwise (for piles with dependency scouting) a recomputation may be scheduled; either way `set` returns the *current* `get` rather than the rejected value.
- Any other `RuntimeException` is logged at `SEVERE` ("Exception in applyCorrection") and the change rejected.

Note `Independent.set0`'s veto branch merely `printStackTrace`s and returns `get` — it does **not** honor `VetoException.revalidate`. Correction is also applied on the recomputation path and via `SealPile`/`TransformableValue` delegations.

`WriteValue` re-declares `applyCorrection` **without** `throws VetoException`; the veto is caught inside the implementation, so callers of `WriteValue#applyCorrection` don't see a checked veto. See [`WriteValue` § correction](WriteValue.md).

## Salient / surprising behavior

- **The stored value can differ from the written value**, and a write can be silently dropped (veto) — always use the **return value of `set`**.
- **`VetoException` is intentionally not logged; every other corrector exception is** (`CorrigibleValue.java`, enforced in `PileImpl.java`).
- **Order matters**: correctors compose left-to-right. A normalizing corrector placed after a vetoing one only runs if the earlier one let the value through.
- **`revalidate`-flagged vetoes can loop**: `VetoException`'s own Javadoc warns that a veto triggered *by* a recomputation must never set `revalidate`, or you get an endless reject-then-recompute cycle.

## Caveats & gotchas

- `_addCorrector` is **append-only** — there is no remove. Once installed (typically by a builder), a corrector stays for the value's life.
- `Independent`'s veto handling does not respect `VetoException.revalidate` and just prints the stack trace, unlike `PileImpl` — behavior differs by implementation.
- The `_`-prefix on `_addCorrector` marks it as low-level/framework-facing access control the language can't enforce; prefer the builder methods (`ICorrigibleBuilder.corrector(...)`, `.bounds(...)`, `.neverNull`).
- A corrector that reads other reactive values (as `applyBounds` does) can veto purely because a *bound* is momentarily invalid; the write fails for reasons unrelated to the written value itself.

## Common tasks (how to…)

- **Reject `null` writes:** builder `.neverNull`, or `_addCorrector(v -> { if(v==null) throw new VetoException(...); return v; })`.
- **Clamp into a range:** builder `.bounds(lower, upper)` / `.lowerBound(...)` / `.upperBound(...)` (requires an `ordering`), which install clamping + consistency correctors.
- **Normalize an incoming value:** `_addCorrector(v -> normalize(v))` (or builder `.corrector(...)`) returning the adjusted value.
- **Veto with a side effect of recompute:** throw `new VetoException(/*revalidate=*/true, msg)` from a corrector — but only where the veto cannot be triggered by the recomputation itself.
- **Inspect what a write would become:** call `applyCorrection(candidate)` directly (catch `VetoException`).

## Tech debt / warts

- `WriteValue#applyCorrection` drops the `throws VetoException` that `CorrigibleValue#applyCorrection` declares — an inconsistent signature across the two aspects; see [overview § caveats](../../overview.md).
- `Independent` and `PileImpl` diverge on veto handling (stack-trace-and-ignore vs. honor `revalidate`) — `Independent.java` vs. `PileImpl.java`.
- No way to remove or reorder a corrector after the fact (`_addCorrector` is the only mutator, `CorrigibleValue.java`).
- Javadoc `@see VetoException` on `_addCorrector` is attached as a `@see` rather than `@throws`, so tooling renders it oddly.

## Related

- [`WriteValue`](WriteValue.md) — correction runs on its `set` path; see § ways a write can be refused/ignored/redirected.
- [`VetoException`](VetoException.md) — the exception a corrector throws to refuse a write (and its `revalidate` flag).
- `ICorrigibleBuilder` (`pile.builder`) — the user-facing way to install correctors (`.corrector`, `.bounds`, `.neverNull`).
- [overview.md](../../overview.md) — architecture map.
