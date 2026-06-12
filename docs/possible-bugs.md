# Pile — possible bugs / suspicious findings

A running log of **suspected, unverified** bugs and suspicious code noticed while documenting Pile (via reading + the language server, *not* by running code). **Each needs the developer's judgment before acting — some may well be intentional.** Don't "fix" from this list without confirming.

Maintenance: documentation subagents report a `SUSPECTED_BUGS` field; the orchestrator appends entries under **Open**. Once judged: move to **Fixed (pending verification)** when a code change has been applied (but not yet test-verified), or to **Resolved / dismissed** with a note when it's not a bug.

## Open

### PB-2 — `RunnableSoftReference extends WeakReference` (soft vs weak mismatch)
- **Where:** the association / reference machinery around `pile.aspect.HasAssociations` (a class named `RunnableSoftReference`; confirm the exact location).
- **Symptom:** a class named `RunnableSoftReference` extends `WeakReference`, not `SoftReference`. Soft and weak references have different GC semantics (soft survive until memory pressure; weak are cleared at the next GC).
- **Confidence:** medium (could be deliberate naming). **Impact:** associations intended to be *softly* held may be cleared eagerly.
- **Found:** `HasAssociations` doc.

### PB-3 (doc-only) — `LastValueRememberSuppressible._COLLECTION` javadoc names the wrong type
- **Where:** `src/pile/aspect/LastValueRememberSuppressible.java` (the `_COLLECTION` method-handle constant).
- **Symptom:** javadoc mis-names the element type as `AutoValidationSuppressible` (copy-paste); the generics are correct.
- **Confidence:** high (a doc typo). **Impact:** none at runtime; misleading javadoc.
- **Found:** `LastValueRememberSuppressible` doc.

### PB-4 — garbled public method names `writableBufferDtSealableString` / `writableWeakBufferDtSealableString`
- **Where:** `Piles.writableBufferDtSealableString` and `writableWeakBufferDtSealableString`.
- **Symptom:** these `…String` twins carry a garbled `DtSealable` infix, inconsistent with every other `…String` twin (e.g. `writableBufferString` expected). Looks like a copy-paste/rename slip in a **public** API name.
- **Confidence:** medium-high. **Impact:** ugly/confusing public API; renaming is a breaking change.
- **Found:** `Piles` index.

### PB-5 (fragile) — `FULFILL_NULL` is an empty lambda identical to `FULFILL_INVALID`
- **Where:** `Piles.FULFILL_INVALID` and `Piles.FULFILL_NULL`, both `()->{}`.
- **Symptom:** two indistinguishable empty-lambda sentinels distinguished only by reference identity in `AbstractPileBuilder`. Probably intended, but fragile.
- **Confidence:** low it's a bug (likely deliberate). **Impact:** subtle if a refactor ever collapses identical lambdas.
- **Found:** `Piles` index.

### PB-6 (cosmetic) — vestigial unused `<E>` type parameters on constant factories
- **Where:** `Piles.getConstant` and the `constant(Boolean/Double/Integer/String)` overloads.
- **Symptom:** declared with an unused `<E>` type parameter. Harmless dead generics.
- **Confidence:** high (it's dead code). **Impact:** none at runtime.
- **Found:** `Piles` index.

### PB-7 — `Independent.__beginTransaction(boolean)` ignores its argument
- **Where:** `src/pile/impl/Independent.java`.
- **Symptom:** `__beginTransaction(boolean b)` ignores `b` and always calls `super.beginTransaction(true)`. The `invalidate` flag is silently dropped.
- **Confidence:** medium. **Impact:** likely nil in practice (an `Independent` has no invalidity), but the dead parameter suggests an unintended divergence from the `DoesTransactions` contract.
- **Found:** `Independent` doc.

### PB-8 (cosmetic) — `Independent` logger name misspelled "Indepednent"
- **Where:** `src/pile/impl/Independent.java`.
- **Symptom:** the `Logger` is registered under the misspelled category name `"Indepednent"`.
- **Confidence:** high (typo). **Impact:** none beyond an odd log-category name.
- **Found:** `Independent` doc.

### PB-9 — `Independent` ignores `VetoException.revalidate` (diverges from `PileImpl`)
- **Where:** `src/pile/impl/Independent.java`.
- **Symptom:** a vetoed correction in `set0` only `printStackTrace()`s and ignores `VetoException.revalidate`, whereas `PileImpl` honors it (re-validates). Documented as a caveat in `CorrigibleValue.md`.
- **Confidence:** low (probably intentional — an `Independent` can't recompute). **Impact:** `revalidate=true` vetoes are no-ops on an `Independent`.
- **Found:** `CorrigibleValue` + `Independent` docs.

### PB-10 — misspelled public API `autoCompundName`
- **Where:** `src/pile/impl/PileCompound.java` (and every override across the compound/list family).
- **Symptom:** the abstract method name is missing an 'o' (should be `autoCompoundName`). Baked into the contract and all overrides.
- **Confidence:** high. **Impact:** permanent public-API wart; renaming is breaking.
- **Found:** `PileCompound` doc.

### PB-12 (doc-only) — assorted javadoc copy-paste slips
- `PileList.java` — class javadoc says "Concrete implementation of `PileList`" (self-referential; should be `AbstractValueList`).
- `AbstractValueList.java` — `removeIf` javadoc copy-pasted from `clear` ("Clear the list…").
- `PileCompound.java` — `makeHead(...)` javadoc documents its `ValueListener` params as "An optional PileList".
- **Confidence:** high (doc typos). **Impact:** misleading javadoc only.
- **Found:** composites wave.

### PB-13 — valid-buffer subscribes to value changes but not validity (author-flagged)
- **Where:** `IIndependentBuilder.setupValidBuffer` / `setupWritableValidBuffer`.
- **Symptom:** the buffer subscribes only to the leader's *value* listener, not its *validity*; when the leader transitions valid↔invalid without a value-change event, the buffer may never refresh. Carries the author's own `//TODO: Why does the buffer sometimes seem to fail to update?` and a commented-out `leader.validity.addValueListener(cl); //FIX?`.
- **Confidence:** medium (author-flagged). **Impact:** stale buffer on validity-only transitions.
- **Found:** `IIndependentBuilder` doc.

### PB-15 — dead `vl=null` / unreachable `isSealed` branch in bounds re-clamp
- **Where:** `src/pile/builder/AbstractIndependentBuilder.java`.
- **Symptom:** in the depend-on-bounds re-clamp block, `if(value.isDefaultSealed()) vl=null;` (no braces/`else`) is immediately overwritten by an unconditional `vl = e->value.set(value.get())`, so the `vl=null` is dead and the `if(vl!=null)` guard can never skip the sealed case. The whole `if(value.isSealed())` arm is also unreachable (the ctor rejects a sealed value and sealing happens later). Looks like a missing `else`/`return`.
- **Confidence:** medium. **Impact:** the intended "skip re-clamp when sealed" path never runs (likely benign since the value isn't sealed yet, but the code is wrong).
- **Found:** `AbstractIndependentBuilder` doc.

### PB-19 (low / doc) — assorted builder slips
- `ISealPileBuilder` — `setupBuffer`/`setupWeakBuffer` push the leader via `setter.accept(value)` while the writable twins (`setupWritableBuffer`/`setupWritableWeakBuffer`) use `setter.set(value)`; the `accept`/`set` split may be intentional but looks like a possible copy-paste inconsistency.
- `SealPileBuilder.java` — class javadoc claims it implements `IIndependentBuilder` (it builds `SealPile`s); copy-paste doc slip.
- `PileBuilder.java` — `@param value` tag names a parameter declared `v`.
- **Confidence:** mixed (mostly doc/cosmetic). **Impact:** misleading docs; the `accept`/`set` one is worth a glance.
- **Found:** builder wave.

### PB-20 — broken null-guard `_thisDependsOn==null && !_thisDependsOn.contains(d)`
- **Where:** `PileImpl` — `dependencyBeginsChanging`, `escalateDependencyChange`, `dependencyEndsChanging` (the same guard in all three).
- **Symptom:** the diagnostic guard dereferences `_thisDependsOn` inside the same `&&` that null-checks it: `if(_thisDependsOn==null && !_thisDependsOn.contains(d))`. If `_thisDependsOn` were null this NPEs; and the "`d` is not a dependency" warning it guards can never fire correctly. Intent was almost certainly `_thisDependsOn != null && !_thisDependsOn.contains(d)`.
- **Confidence:** high (clearly inverted logic). **Impact:** latent — the value almost always has `_thisDependsOn` non-null when these run, so the broken branch is a dead/incorrect diagnostic rather than a live crash.
- **Found:** `Recomputer` doc (opportunistic).

### PB-26 (suspicious) — deferred/queued bracket nop-metadata mixes up keep/remain
- **Where:** `src/pile/aspect/bracket/DeferredValueBracket.java` and `QueuedValueBracket.java`.
- **Symptom:** `openIsNop` returns `keep==null & !backDoesOpen` but `open`'s return is driven by `remain` (not `keep`); `closeIsNop` returns `remain==null & !backDoesClose` but `close`'s return is driven by `keep`. The keep/remain guards appear swapped, so the nop metadata can be wrong when exactly one of keep/remain is non-null. Duplicated across both twins (possibly intentional, but suspicious).
- **Confidence:** low-medium. **Impact:** the framework's nop-optimization may skip/keep a bracket incorrectly in edge cases.
- **Found:** `DeferredValueBracket` + `QueuedValueBracket` docs.

### PB-31 — `PrefInterop.rememberEnum`'s `STORE_NULL` branch is a dead no-op
- **Where:** `src/pile/interop/preferences/PrefInterop.java`, the `LastValueRememberer<E>.storeLastValue` returned by `rememberEnum`.
- **Symptom:** the `STORE_NULL` case sets `s = ""` then `return`s immediately, so the trailing `node.put(key, s)` never runs. The branch appears to store `""` but writes nothing.
- **Confidence:** medium-high. **Impact:** with `NullBehavior.STORE_NULL`, a null enum is silently not persisted (the prior stored value survives).
- **Found:** `PrefInterop` doc.

### PB-32 — `PrefInterop.rememberString` silently drops a `STORE_NULL` write when assertions are off
- **Where:** `src/pile/interop/preferences/PrefInterop.java`, the `LastValueRememberer<String>.storeLastValue` returned by `rememberString`.
- **Symptom:** unlike the primitive factories (which reject `STORE_NULL` at construction), `rememberString` accepts it; its `STORE_NULL` switch case is `assert false; return;`. With assertions disabled a `STORE_NULL` null-write silently does nothing instead of storing (likely intent: store `null`/`""`, or reject at construction like the primitive variants).
- **Confidence:** medium. **Impact:** inconsistent `STORE_NULL` handling for the String factory; silent no-op in production (assertions off).
- **Found:** `PrefInterop` doc.

### PB-36 — `ImplSwitchableRelation.disable()` enables instead of disables on first suppressor
- **Where:** `src/pile/relation/ImplSwitchableRelation.java`, `disable()`.
- **Symptom:** when acquiring the **first** suppressor (`suppressors == 1`), the method reads `v = shouldBeEnabled.getValidOrThrow()` and does `if (v != null) setEnabled.accept(v)` — pushing the `shouldBeEnabled` value (i.e. `true` when the relation *should* be on) into `isEnabled`. But a suppressor is now held, so the relation must be **off**. The first-acquire branch never forces `false` for the suppressor it just added (only the already-had-a-suppressor `else` path no-ops, and the release callback correctly re-checks `suppressors == 0`). The parallel `sbeChanged` handler does `if (suppressors > 0) v = false;` — the missing mirror of that here is the defect.
- **Confidence:** medium-high. **Impact:** acquiring the first `disable()` suppressor while `shouldBeEnabled == true` momentarily flips `isEnabled` to `true` instead of `false`, so the relation briefly (or until the next event) keeps enforcing while supposedly suppressed.
- **Found:** `ImplSwitchableRelation` doc.

### PB-41 — `PileString.RightmostFulfilling.NOT_NULL` is wrongly a `LeftmostFulfilling`
- **Where:** `src/pile/specialized_String/PileString.java`, the inner class `RightmostFulfilling`, static field `NOT_NULL`.
- **Symptom:** the field is declared as type `LeftmostFulfilling` and constructed as `new LeftmostFulfilling(...)` — a copy-paste slip from the sibling `LeftmostFulfilling.NOT_NULL`; it should be a `RightmostFulfilling`. The instance method `RightmostFulfilling.apply` is correct (right-biased), so only the (apparently unused) static constant is wrong.
- **Confidence:** medium-high (subagent read). **Impact:** low — the constant appears unused; left-biased if anyone does use it.
- **Found:** `specialized_String` index.

> Minor (not logged as PB): `ReadDependencyInt.times(int)` javadoc says it delegates to a non-existent `PileInt#multiplyRO` (body correctly calls `PileInt.multiply`); stale `@link`. Noted in the `specialized_int` doc as a wart. The `Recomputations.isRecomputationfinished` misspelling (lowercase `f`) is likewise left as a wart (fixed the `static` defect, see PB-21, but didn't rename the public method).

## Fixed (pending verification — 2026-06-12)

Code changes applied (Tier A) but **not yet test-verified**. Reviewed via diff; awaiting characterization tests / a compile+run pass.

### PB-1 — `ReadWriteListenValue.validBuffer_memo` returned a read-only buffer despite writable intent
- **Where:** `src/pile/aspect/combinations/ReadWriteListenValue.java`.
- **Symptom:** the javadoc says it delegates to `writableValidBuffer_memo`, but the body called `readOnlyValidBuffer_memo` (inherited from the read-only parent), so `asDependency` on a writable value yielded a read-only memoized buffer.
- **Fixed:** `validBuffer_memo()` now calls `writableValidBuffer_memo()` (matches the file-wide pattern of overriding read-only methods to their writable twin).

### PB-21 — `Recomputations` forwarding helpers declared non-`static`
- **Where:** `src/pile/aspect/recompute/Recomputations.java`.
- **Symptom:** 13 forwarding helpers (`fulfillInvalid`, `fulfillRestoreOldValue`, `restoreOldValue`, `fulfillNull`, `getOldValue`, `forgetOldValue`, `isRecomputationfinished`, `hasOldValue`, `queryChangedDependencies`, incl. overloads) were `public` instance methods on an otherwise all-static utility class.
- **Fixed:** added `static` to all 13 (every sibling is static; they use only static state). The `isRecomputationfinished` misspelling was left as a separate wart.

### PB-22 — `ListenerManager(boolean sorting)` dropped its argument
- **Where:** `src/pile/aspect/listen/ListenValue.java` (`ListenerManager` nested class).
- **Symptom:** the `(boolean sorting)` constructor never assigned `this.sorting`, so `new ListenerManager(true)` behaved like `new ListenerManager()`.
- **Fixed:** added `this.sorting=sorting;` (mirrors the `(Object, boolean)` constructor).

### PB-23 — rate-limited `MultiListenValue` discarded the collected sources
- **Where:** `src/pile/aspect/listen/ConcreteMultiListenValue.java`.
- **Symptom:** the `careAboutSources=true` branch fired `new ValueEvent(manager.getValueEventSource())`, ignoring the accumulated `MultiEvent e`, so it behaved like `careAboutSources=false`.
- **Fixed:** now fires `new ValueEvent(e)` — the `MultiEvent` becomes the event's source (it carries `isSource`/`getSources`/`allSources`), honoring the javadoc. (`MultiEvent` is not a `ValueEvent`, so it must be wrapped — an earlier `fireValueChange(e)` attempt was a compile error and was corrected.)

### PB-24 — any-value bracket opened on the old value leaked
- **Where:** `src/pile/impl/AbstractReadListenDependency.java`, `_addAnyValueBracket` (the `!valid && oldValid` branch).
- **Symptom:** the branch opened the bracket on the old value but guarded `activeAnyBracketsOnOld.add(b)` with `if(valid)` (always false there), so the bracket was never recorded and never closed.
- **Fixed:** guard changed to `if(oldValid)` (matches the `valid && oldValid` branch's old-value bookkeeping).

### PB-25 — `FilteredBracket.close` tested the wrong filter
- **Where:** `src/pile/aspect/bracket/FilteredBracket.java`.
- **Symptom:** `close` guarded `closeFilter != null` but then called `openFilter.test(value)` — half-updated copy of `open`; would NPE / break symmetry for the asymmetric `nopOnNull{Open,Close}` factories.
- **Fixed:** `close` now calls `closeFilter.test(value)`. (The bug-log's original suggested repair — changing the guard to `openFilter` — was rejected as it would orphan the `closeFilter` field.)

### PB-27 — `QueuedValueBracket.getDefaultQueue` had broken double-checked locking
- **Where:** `src/pile/aspect/bracket/QueuedValueBracket.java`.
- **Symptom:** the in-lock check tested an outside-lock read of `local` (never re-read the field) and assigned `defaultQueue` unconditionally — two racers each created a queue and the second clobbered the first.
- **Fixed:** rewrote as canonical DCL — re-read `defaultQueue` inside the `synchronized` block, and assign only when newly created.

### PB-29 — `SynchronizingFilesBackedValue.STRING_CODEC` NPE'd on every read/write
- **Where:** `src/pile/interop/preferences/SynchronizingFilesBackedValue.java`, `STRING_CODEC`.
- **Symptom:** `encode`/`decode` computed `os`/`is` (= `Files.new…Stream(path)` when the param is null) but then wrapped the raw `useThis` (always null when called) → NPE.
- **Fixed:** wrap the computed `os`/`is`. (`viaString` delegates to `STRING_CODEC`, so it's fixed too.)

### PB-30 — `SynchronizingFilesBackedValue.autoPoll` null-checked the wrong reference
- **Where:** `src/pile/interop/preferences/SynchronizingFilesBackedValue.java`, static `autoPoll(...)`.
- **Symptom:** checked `if(pollfRef==null)` (the never-null holder) instead of `deref` (the weak referent), so it NPE'd after GC instead of self-cancelling.
- **Fixed:** `if(deref==null)` (mirrors the `condRef` branch above).

### PB-33 — `SequentialQueue.isQueueWorkerThread` compared a `Thread` to a `Future`
- **Where:** `src/pile/utils/SequentialQueue.java`, `isQueueWorkerThread()`.
- **Symptom:** `Thread.currentThread() == queueWorkerFuture` (a `Future`) — compiles only because `Thread` isn't `final`; always false at runtime.
- **Fixed:** compares `queueWorkerThread`. **Note (B1):** the only caller chain is `isDeferThread()` in `JSceneViewerImpl`/`MSceneViewerImpl`, which `find_references` shows is itself **uncalled** (dead code today) — so this fix has no current runtime impact on Biss; it just makes the method correct for when `isDeferThread()` is wired in.

### PB-34 — `SequentialQueue.shutdownNow` NPE'd before the first `enqueue`
- **Where:** `src/pile/utils/SequentialQueue.java`, `shutdownNow()`.
- **Symptom:** `new ArrayList<>(q)` with no `q==null` guard; `q` is lazily created on first `enqueue`.
- **Fixed:** `q==null ? new ArrayList<>() : new ArrayList<>(q)`, then `if(q!=null) q.clear();` (constructor-copy form, per preference).

### PB-35 — `BooleanGroup_Max1.afterChange` callback never fired on changes
- **Where:** `src/pile/relation/BooleanGroup_Max1.java`, `add(...)`.
- **Symptom:** `callback.run()` sat in the `if(cl==null)` registration block, firing once at add-time, never on subsequent events (unlike `Exactly1`/`Min1`).
- **Fixed:** moved `callback.run()` to the end of the listener lambda.

### PB-38 — `PileInt.addRO` dropped the operand
- **Where:** `src/pile/specialized_int/PileInt.java`, `addRO(ReadDependency, int)`.
- **Symptom:** `op.mapToInt(o -> o==null ? null : +value)` returned the constant `value` (unary `+`), not `o+value`; propagated through `add`/`subtractRO`/`subtract`.
- **Fixed:** `+value` → `o+value`.

### PB-39 — `PileDouble.inverse{RW,RO}` computed negation instead of reciprocal
- **Where:** `src/pile/specialized_double/PileDouble.java`, `inverseRW`/`inverseRO`.
- **Symptom:** recompute did `reco.fulfill(-v)` (copy-paste from `negative*`); should be `1/v`.
- **Fixed:** both recompute bodies now `reco.fulfill(1/v)` (the `negative*` methods correctly keep `-v`).

### PB-40 — `PileDouble.divideRW` was read-only despite its `RW` contract
- **Where:** `src/pile/specialized_double/PileDouble.java`, `divideRW(ReadWriteDependency<Double>, double)`.
- **Symptom:** delegated to `multiplyRO(op, 1/value)` (no write-back) despite the `RW` name/javadoc.
- **Fixed:** delegates to `multiplyRW(op, 1/value)` (installs the bijection write-back).

## Author-flagged uncertainties (in-source TODOs — not necessarily bugs)
- **`ISealPileBuilder.setupWritableRateLimited`** — `src/pile/builder/ISealPileBuilder.java` carries the author's note *"Invalidating the buffer directly does not work yet"* (acknowledged-incomplete behavior).
- **Writable `field`/`deref` ↔ inner-value sync** — the `//TODO` near `ISealPileBuilder.java` concerns whether the field view stays consistent with its inner value when the inner value rejects/redirects/corrects a write (NOT the no-inner-value case, which is intentional silent-ignore — see PB-18 dismissed).

- **`Dependency.suppressDeepRevalidation` propagation** — `src/pile/aspect/Dependency.java` carries the author's own TODO *"The last bit should maybe behave different?"* The suppression's recursion-propagation half is flagged uncertain by the author.

## To verify with a characterization test (behavior, not a known bug)

- **Transaction / manual value retention:** when an invalid dependency `X` *recomputes* to a genuinely different value while a depender `D` was manually `set` valid — does `D` keep its manual value? Strongly indicated by the code (valid-branch cancels the scheduled recompute) but worth a golden test. See [concepts/transactions.md](concepts/transactions.md).

## Resolved / dismissed

- **PB-11 (dismissed)** — `PileList`'s value-based `add`/`set` throwing `UnsupportedOperationException` (because `wrap` isn't overridden). **Not a bug:** lists are allowed to be immutable / throw `UnsupportedOperationException` for unsupported mutation (standard `Collection` contract); `PileList`'s box-only API (`addV`/`setV`) is by design. The `PileList` doc still notes the behavior as a usage gotcha. (Dismissed by developer, 2026-06-12.)
- **PB-14 (intended; residual improvement)** — `AbstractPileBuilder`'s staged recomputer re-invoking the user function on the no-continuation error path. **By design (debugging):** it re-runs so you can breakpoint and step into the function to see why it didn't fulfill. *Residual improvement:* gate it behind a `DebugEnabled` flag so production builds don't double-invoke. (Developer, 2026-06-12.)
- **PB-16 (intended; possible improvement)** — no-arg `seal` not resetting a previously-set interceptor/`allowInvalidation`. **By design:** a custom interceptor means "seal *with this behavior*"; a later bare `seal` just confirms the value should be sealed, not *how*. *Possible improvement:* make it commutative, so `seal(redirect)` after a default `seal` matches the reverse order. (Developer, 2026-06-12.)
- **PB-17 (not a bug)** — `AbstractIndependentBuilder` ignoring `seal(.., allowInvalidation=true)`. **Not a bug:** `Independent`s silently ignore invalidation attempts anyway (no invalid state), so the flag is moot. (Developer, 2026-06-12.)
- **PB-18 (not a bug)** — writable `field`/`deref` "dropping" a write when there is no inner value. **Not a bug:** silently ignoring an unsupported write is idiomatic in Pile (cf. `Constant`; a field with no inner value has nowhere to write). The inline `//TODO` is about a *different* concern — field↔inner-value sync (see author-flagged). (Developer, 2026-06-12.)
- **PB-28 (dismissed — verified by reading, not a functional bug)** — `ReactiveSuppressionSwitcher.setSuppressed(ReadListenValue newState)` calling `super.setSuppressed(state)` (the inherited current state) rather than a boolean derived from `newState`. **Verified against `SuppressionSwitcher.setSuppressed(boolean)` (parent):** that method sets `state` *directly* and its main job is the side effect of clearing `suppressThese` + releasing all suppressors — which happens regardless of the boolean. The requested state is then (re)applied on the *next line* by `_setSuppressedState(newState, true)`, which runs the `updater` and pushes `isTrue(newState)` into `state`. Traced all old-state × new-state combinations (incl. the `newState == reactiveState` same-object early-return): final `state` and object-release are identical whether `state` or the derived `s` is passed. The collection overloads *must* pass the derived boolean (they suppress a specific collection immediately) — the no-collection overload doesn't. A clarifying comment was added in-source so it isn't re-flagged. (Verified by reading, 2026-06-12.)
- **PB-37 (dismissed — verified by reading, not a bug)** — `AbstractRelation.installEnabledListener` calling `getListener().runImmediately()` (no-arg) where subclasses use `runImmediately(true)`. **Verified against `ValueListener.runImmediately`:** the boolean is `inThisThread` (run synchronously on the caller's thread vs. asynchronously via `StandardExecutors.unlimited()`) — **not** an "initial pass" toggle. Both overloads fire the listener with a `null` event, so the base path *does* re-assert the invariant on re-enable; it just runs it on another thread. The originally-feared "won't re-equalize until next operand change" does not hold. (Residual nuance, not a bug: the async re-assert is unordered relative to the enable; flag only if a future ordering issue surfaces.) (Verified by reading, 2026-06-12.)
