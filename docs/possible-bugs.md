# Pile — possible bugs / suspicious findings

A running log of **suspected, unverified** bugs and suspicious code noticed while documenting Pile (via reading + the language server, *not* by running code). **Each needs the developer's judgment before acting — some may well be intentional.** Don't "fix" from this list without confirming.

Maintenance: documentation subagents report a `SUSPECTED_BUGS` field; the orchestrator appends entries under **Open**. Once judged: move to **Fixed (pending verification)** when a code change has been applied (but not yet test-verified), or to **Resolved / dismissed** with a note when it's not a bug.

## Open

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

### PB-8 (cosmetic) — `Independent` logger name misspelled "Indepednent"
- **Where:** `src/pile/impl/Independent.java`.
- **Symptom:** the `Logger` is registered under the misspelled category name `"Indepednent"`.
- **Confidence:** high (typo). **Impact:** none beyond an odd log-category name.
- **Found:** `Independent` doc.

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

### PB-19 (low / doc) — assorted builder slips
- `ISealPileBuilder` — `setupBuffer`/`setupWeakBuffer` push the leader via `setter.accept(value)` while the writable twins (`setupWritableBuffer`/`setupWritableWeakBuffer`) use `setter.set(value)`; the `accept`/`set` split may be intentional but looks like a possible copy-paste inconsistency.
- `SealPileBuilder.java` — class javadoc claims it implements `IIndependentBuilder` (it builds `SealPile`s); copy-paste doc slip.
- `PileBuilder.java` — `@param value` tag names a parameter declared `v`.
- **Confidence:** mixed (mostly doc/cosmetic). **Impact:** misleading docs; the `accept`/`set` one is worth a glance.
- **Found:** builder wave.

### PB-26 (suspicious) — deferred/queued bracket nop-metadata mixes up keep/remain
- **Where:** `src/pile/aspect/bracket/DeferredValueBracket.java` and `QueuedValueBracket.java`.
- **Symptom:** `openIsNop` returns `keep==null & !backDoesOpen` but `open`'s return is driven by `remain` (not `keep`); `closeIsNop` returns `remain==null & !backDoesClose` but `close`'s return is driven by `keep`. The keep/remain guards appear swapped, so the nop metadata can be wrong when exactly one of keep/remain is non-null. Duplicated across both twins (possibly intentional, but suspicious).
- **Confidence:** low-medium. **Impact:** the framework's nop-optimization may skip/keep a bracket incorrectly in edge cases.
- **Found:** `DeferredValueBracket` + `QueuedValueBracket` docs.

> Minor (not logged as PB): `ReadDependencyInt.times(int)` javadoc says it delegates to a non-existent `PileInt#multiplyRO` (body correctly calls `PileInt.multiply`); stale `@link`. Noted in the `specialized_int` doc as a wart. The `Recomputations.isRecomputationfinished` misspelling (lowercase `f`) is likewise left as a wart (fixed the `static` defect, see PB-21, but didn't rename the public method).

## Fixed (pending verification — 2026-06-12)

Code changes applied (Tier A) but **not yet test-verified**. Reviewed via diff; awaiting characterization tests / a compile+run pass.

### PB-1 — `ReadWriteListenValue.validBuffer_memo` returned a read-only buffer despite writable intent
- **Where:** `src/pile/aspect/combinations/ReadWriteListenValue.java`.
- **Symptom:** the javadoc says it delegates to `writableValidBuffer_memo`, but the body called `readOnlyValidBuffer_memo` (inherited from the read-only parent), so `asDependency` on a writable value yielded a read-only memoized buffer.
- **Fixed:** `validBuffer_memo()` now calls `writableValidBuffer_memo()` (matches the file-wide pattern of overriding read-only methods to their writable twin).

### PB-2 — `RunnableSoftReference extends WeakReference` (soft policy acted weak)
- **Where:** `src/pile/aspect/HasAssociations.java`, nested `ReferencePolicy.RunnableSoftReference`.
- **Symptom:** the class — name *and* javadoc both say `SoftReference` — extended `WeakReference` (a copy-paste from the sibling `RunnableWeakReference`). The `SOFT` policy instantiates it whenever a `Runnable` cleanup is attached, so SOFT-with-cleanup held its referent **weakly** (cleared at the next GC) instead of softly (survives until memory pressure).
- **Fixed:** `extends WeakReference<T>` → `extends SoftReference<T>` (same two constructors; `SoftReference` already imported and used by the `SOFT` policy).

### PB-13 — valid-buffer didn't subscribe to leader validity (author-flagged; isolated commit)
- **Where:** `src/pile/builder/IIndependentBuilder.java`, `setupValidBuffer` and `setupWritableValidBuffer`.
- **Symptom:** the update listener `cl` was registered only on `leader.addValueListener` (value events), not `leader.validity()`. A "last valid value" buffer was left stale when the leader went invalid→valid without a value-change event — the author's own `//TODO: Why does the buffer sometimes seem to fail to update?` + a commented-out `//FIX?`.
- **Fixed (UNCERTAIN — committed separately so it can be reverted independently):** enabled `leader.validity().addValueListener(cl)` in both builders and extended each cleanup to also `leader.validity().removeValueListener(cl)`. `cl` already guards `if(leader.isValid())`, so firing on validity changes is safe (valid→copy, invalid→no-op; at worst a harmless duplicate copy). Developer was unsure why the `//FIX?` was left commented; isolated pending verification.

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

### PB-15 — dead `vl=null` from a missing `else` (bounds re-clamp)
- **Where:** `src/pile/builder/AbstractIndependentBuilder.java`, `build()`.
- **Symptom:** `if(value.isDefaultSealed()) vl=null;` was immediately overwritten by an unconditional `vl = e->value.set(value.get())`, so a default-sealed (read-only) value still got a re-clamp listener.
- **Fixed:** added the missing `else` so `vl` stays `null` for default-sealed values. (The enclosing `if(value.isSealed())` arm's reachability at build time is unchanged/uncertain per the original note, but the branch is now correct if reached.)

### PB-20 — inverted null-guard `_thisDependsOn==null && !…contains(d)`
- **Where:** `src/pile/impl/PileImpl.java`, `dependencyBeginsChanging`/`escalateDependencyChange`/`dependencyEndsChanging`.
- **Symptom:** the "d is not a dependency" diagnostic guard was inverted — non-null short-circuited to false (never warned); null would NPE on `.contains`.
- **Fixed:** `==null` → `!=null` in all three (a `System.err` diagnostic; now fires correctly, no NPE risk).

### PB-31 — `PrefInterop.rememberEnum` `STORE_NULL` stored nothing
- **Where:** `src/pile/interop/preferences/PrefInterop.java`, `rememberEnum.storeLastValue`.
- **Symptom:** `case STORE_NULL: s=""; return;` returned before `node.put`, so it persisted nothing.
- **Fixed:** `return` → `break`, so `""` is stored (recall already maps `""`→null). Part of the partial `STORE_NULL` support (see PB-32).

### PB-32 — `PrefInterop.rememberString` `STORE_NULL` partial support (per developer design)
- **Where:** `src/pile/interop/preferences/PrefInterop.java`, `rememberString.storeLastValue`/`recallLastValue`.
- **Symptom:** `case STORE_NULL: assert false; return;` — silently no-op'd with assertions off.
- **Fixed:** `STORE_NULL` now stores null as `""`; real all-`'\0'` values (incl. `""`) are escaped with a trailing `'\0'` on store and un-escaped on recall, so they don't collide with null's `""` encoding. Added the `isAllNul` fast early-exit helper; updated the `NullBehavior.STORE_NULL` javadoc to document partial support (primitives still reject it). Forbidding/redirecting null is left to client correctors.

### PB-36 — `ImplSwitchableRelation.disable()` enabled on the first suppressor
- **Where:** `src/pile/relation/ImplSwitchableRelation.java`, `disable()`.
- **Symptom:** acquiring the first suppressor read `shouldBeEnabled` and pushed it into `isEnabled` (true when it should be on) instead of forcing disabled.
- **Fixed:** on first suppressor, set `v=false` (disable); subsequent acquires stay `null` (no-op). Mirrors the `sbeChanged` handler's `if(suppressors>0) v=false`.

### PB-41 — `PileString.RightmostFulfilling.NOT_NULL` was a `LeftmostFulfilling`
- **Where:** `src/pile/specialized_String/PileString.java`.
- **Symptom:** the static `NOT_NULL` inside `RightmostFulfilling` was declared/constructed as `LeftmostFulfilling`.
- **Fixed:** type + constructor → `RightmostFulfilling` (field name unchanged; not a rename). `RightmostFulfilling` has the matching `(Predicate, String)` constructor.

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
- **PB-7 (dismissed — verified by reading, not a bug)** — `Independent.__beginTransaction(boolean b)` "ignoring" its argument. **Verified:** the contract param is `invalidate`, but `super.beginTransaction(true)` resolves to `AbstractReadListenDependency.beginTransaction(boolean workInformQueue)` — a *different* boolean (whether to call `__workInformQueue()` afterwards). `Independent` legitimately ignores `invalidate` (it has no invalid state to enter, so the flag is moot) and correctly passes `workInformQueue=true` (the right default for a caller not holding `mutex`). Forwarding `b` here would be semantically wrong (conflating the two booleans). No change. (Verified by reading, 2026-06-12.)
- **PB-9 (dismissed — confirmed by developer, not a bug)** — `Independent` ignoring `VetoException.revalidate` in `set0` (only `printStackTrace()` + `return get()`). **Independents can't revalidate** (developer-confirmed): `Independent.revalidate()` is an empty no-op, and an `Independent` is always valid / never recomputes, so honoring the `revalidate` flag would do nothing. The divergence from `PileImpl` is correct for a leaf value. (Residual wart **fixed** at developer's request: the veto `catch` now uses `log.log(Level.WARNING, …, x)` instead of `printStackTrace()`, matching the sibling `RuntimeException catch`.) (Developer-confirmed, 2026-06-12.)
- **PB-37 (dismissed — verified by reading, not a bug)** — `AbstractRelation.installEnabledListener` calling `getListener().runImmediately()` (no-arg) where subclasses use `runImmediately(true)`. **Verified against `ValueListener.runImmediately`:** the boolean is `inThisThread` (run synchronously on the caller's thread vs. asynchronously via `StandardExecutors.unlimited()`) — **not** an "initial pass" toggle. Both overloads fire the listener with a `null` event, so the base path *does* re-assert the invariant on re-enable; it just runs it on another thread. The originally-feared "won't re-equalize until next operand change" does not hold. (Residual nuance, not a bug: the async re-assert is unordered relative to the enable; flag only if a future ordering issue surfaces.) (Verified by reading, 2026-06-12.)
