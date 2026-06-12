# `pile.aspect.VetoException`

Source folder: `src`. File: `pile/aspect/VetoException.java`.

The **exception a corrector throws to refuse a write** — optionally asking the value to revalidate (recompute) instead. It is the signal half of the correction mechanism: [`CorrigibleValue`](CorrigibleValue.md) runs a chain of correctors over a candidate value, and any corrector may abort the whole write by throwing this.

See the [overview](../../overview.md) for where correction sits, and [`WriteValue`](WriteValue.md) for the `set` path that catches it.

## What it is

A small `RuntimeException` subclass. It carries no logic beyond one extra field; its meaning is entirely "this write must not proceed". Because it extends `RuntimeException` it is **unchecked** — but `CorrigibleValue#applyCorrection` nonetheless *declares* `throws VetoException` to document the contract. (Note: [`WriteValue#applyCorrection`](WriteValue.md) drops that `throws` clause; the veto is caught inside the implementation rather than propagated.)

## Field

- `public final boolean revalidate` — whether the veto should additionally trigger a **revalidation** of a value that supports invalidity. When set, instead of merely abandoning the write, the value is asked to recompute itself (see *Who handles it*). The Javadoc warns: **if a recomputation itself can trigger such a veto, you get an endless reject-then-restart cycle** — make sure a `revalidate`-veto is never raised by the recomputation it would re-trigger.

## Constructors

Eight constructors: four take the `revalidate` flag explicitly together with the usual `(msg, cause)` combinations; the other four omit it and **default `revalidate` to `false`** by delegating. So a bare `new VetoException` or `new VetoException("msg")` refuses the write without requesting recomputation.

## Checked vs. unchecked

**Unchecked** (`extends RuntimeException`, `VetoException.java`). It can be thrown from a plain `Function` corrector (`CorrigibleValue._addCorrector` takes an ordinary `Function`, `CorrigibleValue.java`) without a checked-exception declaration. The `throws VetoException` on `CorrigibleValue#applyCorrection` is documentation/contract, not a compiler requirement.

## Who throws it (correctors)

Correctors registered on a [`CorrigibleValue`](CorrigibleValue.md) throw it from `applyCorrection` to reject the candidate value. In practice these are wired up through the builders (`AbstractPileBuilder`, `AbstractIndependentBuilder`, `ICorrigibleBuilder`) and thrown by application code (e.g. Biss models reject out-of-range values).

## Who handles it (the set path)

Two call sites in `PileImpl` run the corrector and catch the veto:

- **Direct write — `PileImpl#set`**: wraps `applyCorrection(val)`. On `VetoException`:
  - if `revalidate` is set → calls `revalidate`;
  - else, if a recomputer using dependency-scouting exists → schedules/starts a recomputation;
  - then **returns the current `get`** — the write is abandoned, no value stored, no exception propagated to the caller.
- **Recomputation fulfillment**: wraps `applyCorrection(val)` while fulfilling a recomputation. On `VetoException` it calls `fulfillInvalid` and, if `revalidate` is set, `revalidate`, then **re-throws** the veto.

A non-`VetoException` `RuntimeException` from a corrector is instead **logged at `SEVERE`** ("Exception in applyCorrection") and the change rejected — matching `CorrigibleValue`'s Javadoc that a veto is *not* logged but any other corrector exception is.

## Salient behavior

- **A veto is silent at the call site of `set`.** `set` swallows it and returns the unchanged current value; callers see no exception, only that their value didn't land. Use the return value of `set` to detect this.
- **`revalidate=true` flips a veto from "ignore" to "recompute".** The same veto can mean either, depending purely on this flag.
- **The two handling sites differ:** `set` consumes the veto; recomputation re-throws it after marking the value invalid.

## Caveats

- **Endless-cycle risk** with `revalidate=true` if the recomputation can re-trigger the same veto — the field's own Javadoc flags this.
- Because `set` returns `get` on veto, a refused write is **indistinguishable from a no-op write** unless you compare against the prior value or know your corrector's rules.
- The `revalidate` request is honored only by values that have a concept of invalidity; on values where `revalidate` is a no-op (see [`WriteValue#revalidate`](WriteValue.md)) the flag has no effect.

## Common tasks (how to…)

- **Reject a write outright:** in a corrector, `throw new VetoException("reason");` (revalidate defaults to `false`).
- **Reject and ask the value to recompute instead:** `throw new VetoException(true, "reason");` — but ensure the recomputation can't itself raise this veto.
- **Detect a refused write:** compare `set`'s return value to your input, or to the prior value.

## Tech debt / warts

- The `serialVersionUID` is present but the class carries no real serializable state beyond the boolean.
- Contract inconsistency across the API: `CorrigibleValue#applyCorrection` declares `throws VetoException` while [`WriteValue#applyCorrection`](WriteValue.md) does not (the veto is caught internally) — see [overview § caveats](../../overview.md) on unsystematic API.

## Related

- [`CorrigibleValue`](CorrigibleValue.md) — the correction interface whose correctors throw this.
- [`WriteValue`](WriteValue.md) — the `set` path that catches it; lists veto as one way a write is refused.
- [overview.md](../../overview.md) — architecture map.
