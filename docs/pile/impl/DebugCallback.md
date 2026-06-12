# `DebugCallback`

A debug-only hook interface for tracing the lifecycle of a single reactive value (set/fulfill/transaction/recompute/invalidate events), consulted only when `DebugEnabled.DE`.

Source folder: `src` (package `pile.impl`).

Up: [impl index](_index.md) · [overview](../../overview.md). Related model: [concepts/transactions.md](../../concepts/transactions.md). Host base: [`AbstractReadListenDependency`](AbstractReadListenDependency.md) (ARLD) · [`PileImpl`](PileImpl.md).

## What it's for

`DebugCallback` is an all-`default`-method interface you implement to observe the inner workings of a reactive value. You install one on a value via [`ARLD._setDebugCallback(dc)`](AbstractReadListenDependency.md), which stores it in the public `dc` field. The value's machinery then calls back the matching method at each interesting moment, so you get a per-object trace without a debugger.

Because every method has a default empty body, you override only the events you care about. The interface javadoc itself says: *"Look where the methods are called to understand how to use them"* — the call sites in ARLD / `PileImpl` / `Independent` are the real contract (this doc maps them below), not these one-line signatures.

## Debug-only — `DebugEnabled.DE`-gated

The `dc` field and all its invocations are dead in production builds. Every call site is guarded `if(DE && dc!=null) dc.someMethod(...)`, where `DE` is the `static final boolean` flag `pile.interop.debug.DebugEnabled.DE` (it lives in the `debug` / `debug_off` source folder, **not** `src` — see [overview](../../overview.md) § source folders). Because `DE` is `final`, when it is `false` the compiler strips every callback site (conditional compilation), so toggling debug **requires recompiling the library**. The `dc` field itself is only meaningfully populated under debug; the ARLD javadoc states it "should be ignored if `DebugEnabled.DE` is false".

Net effect: do not rely on `DebugCallback` for any production behavior. It is a tracing aid only.

## How it relates to the `dc` field in ARLD

`dc` is a plain public mutable field on ARLD, default `null`, set through `_setDebugCallback`. It is shared by both ARLD subclasses (`PileImpl`, `Independent`), so callbacks fire from whichever subclass owns the event. The companion debug metadata on ARLD — `creationTrace`, `avName`, and the transaction-reason tracking — is likewise `DE`-only; see the ARLD doc. The field is read directly (not via getter) at the call sites, so installing a callback at runtime is just `value.dc = ...` / `_setDebugCallback(...)`.

## The callback hooks, by category

Rather than enumerate the ~17 methods one by one (the signatures are self-describing and the call site is the contract), here they are grouped by the event they trace. Every method's first argument is the `source` value the event happened to.

- **Set / fulfill (a value being written).**
  - `set(source, val)` — a direct write. Fired from `PileImpl.set` and `Independent.set`.
  - `fulfill(source, val)` — a recomputation delivering a value.
  - `fulfillInvalid(source)` — a recomputation that resolves to *invalid* instead of a value.
- **Invalidate.**
  - `explicitlyInvalidate(source, fromFulfill)` — the value is explicitly invalidated; `fromFulfill` distinguishes invalidation arising from a fulfill path (`PileImpl.java`, `true`) vs a direct `_invalidate` (`PileImpl.java`, , `false`).
- **Transactions.**
  - `beginTransactionCalled(source)` / `endTransactionCalled(source)` — bracket the transaction counter moving. See [concepts/transactions.md](../../concepts/transactions.md) for what a transaction *is*.
- **Dependency-change propagation (a dependency of `source` going in/out of flux).**
  - `dependencyBeginsChanging(source, d, valid)` / `dependencyEndsChanging(source, d)` — `d` is the changing `Dependency`; `valid` is `source`'s async validity at begin.
- **Recomputation scheduling lifecycle.**
  - `newlyScheduledRecomputation(source)` — a recompute is queued.
  - `startPendingRecomputation(source)` — a queued recompute begins.
  - `unschedulePendingRecomputation(source)` — a queued recompute is dropped before starting.
  - `cancellingOngoingRecomputation(source)` — an in-flight recompute is cancelled.
- **Validity firing / revalidation.**
  - `fireValueChange(source)` — a value-change event is about to fire.
  - `fireDeepRevalidate(source)` — deep-revalidate propagation.
  - `revalidateCalled(source)` — `revalidate(...)` was invoked.
- **Auto-validation suppressors.**
  - `autoValidationSuppressorCreated(source, s)` / `autoValidationSuppressorReleased(source, s)` — paired create/release of a `Suppressor` that pauses auto-validation. Note the *released* hook is wired into the suppressor's release lambda, so it fires when the `Suppressor` is closed, not at a fixed code point.
- **Manual aid.**
  - `trace` — not invoked by the framework anywhere; it is a convenience you can call from your own override to dump the current stack to `System.err`.

## Salient / surprising behavior

- **The signatures lie by omission.** The default bodies say nothing about *when* a method fires; only the call sites do. `explicitlyInvalidate`'s `fromFulfill` flag and `dependencyBeginsChanging`'s `valid` flag in particular only make sense once you read the call site.
- **`trace` is orphaned** — never called by the library (unlike all the other methods). It exists purely for you to call manually.
- **`source` is not always `this`.** Most hooks pass `this`, but `cancellingOngoingRecomputation` is fired from inside the recomputation object and passes `outer.get` (the owning `PileImpl`, via a weak ref) — which can in principle be `null` after GC; the call site guards `o!=null`.
- **No `NOP` constant.** A commented-out `public static final DebugCallback NOP` sits at `DebugCallback.java`; since every method is already a no-op default, a bare `new DebugCallback{}` is the NOP. Callers instead just leave `dc==null`.
- **Independent fires far fewer hooks.** Only `set` is wired in `Independent`; the recomputation/dependency-change hooks belong to `PileImpl` (Independent has no recomputation). The `*PendingRecomputation` / `autoValidationSuppressor*` hooks are stubbed away entirely in the non-depender branch (`AbstractReadListenDependency_NoDepender`).

## Caveats & gotchas

- Anything you do inside a callback runs **on the value's own threads and frequently while `mutex` is held** (e.g. the fire/transaction/dependency hooks). Keep overrides cheap and non-blocking; do not call back into the value graph or you risk re-entrancy/deadlock. (Same discipline as brackets — see ARLD doc.)
- The `dc` field is public and unsynchronized; setting it concurrently with events is racy. Set it once at construction/setup.
- Coverage is not exhaustive — these hooks trace the events the author found worth tracing, not every state transition. Absence of a callback does not mean nothing happened.

## Common tasks

- **Trace one value's lifecycle:** build with debug enabled (`DE==true`), then `value._setDebugCallback(new DebugCallback{ @Override public void set(...){...} ... })`, overriding just the events you want.
- **Dump a stack at an event:** from inside your override, call `trace` (the inherited default prints the current stack to `System.err`).

## Tech debt / warts

- Documentation-by-call-site: the interface relies on readers grepping for invocations; there is no per-method javadoc describing semantics.
- Dead `NOP` constant left commented out.
- `trace` mixes a never-called framework hook with a hardcoded `System.err` utility — an odd member of an otherwise pure event interface.
- Public mutable `dc` field with no synchronization or volatility.

## SUPERDOC_CONFLICTS / SUSPECTED_BUGS

None. (`cancellingOngoingRecomputation` at `PileImpl.java` and `autoValidationSuppressorReleased` at  lack a literal inline `DE &&` but are inside enclosing `if(DE)` blocks, so they remain correctly gated.)
