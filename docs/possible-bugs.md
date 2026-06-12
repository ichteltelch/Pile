# Pile — possible bugs / suspicious findings

A running log of **suspected, unverified** bugs and suspicious code noticed while documenting Pile (via reading + the language server, *not* by running code). **Each needs the developer's judgment before acting — some may well be intentional.** Don't "fix" from this list without confirming.

Maintenance: documentation subagents report a `SUSPECTED_BUGS` field; the orchestrator appends entries here. Newest findings go under **Open**; move to **Resolved / dismissed** with a note once judged.

## Open

### PB-1 — `ReadWriteListenValue.validBuffer_memo` returns a read-only buffer despite writable intent
- **Where:** `src/pile/aspect/combinations/ReadWriteListenValue.java`
- **Symptom:** the javadoc says it delegates to `writableValidBuffer_memo`, but the body calls `readOnlyValidBuffer_memo` (identical to the read-only parent `ReadListenValue`). Consequence: `asDependency` on a *writable* value yields a **read-only** memoized buffer.
- **Likely cause:** copy-paste from the read-only parent.
- **Confidence:** medium-high. **Impact:** writes against the buffer-as-dependency are silently unsupported where writable was expected.
- **Found:** combinations wave (`ReadListenValue` + `ReadWriteListenValue` docs).

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

### PB-21 — `Recomputations` convenience methods declared non-`static` (uncallable)
- **Where:** `Recomputations.java` — the recomputation-forwarding helper methods (plus the misspelled `isRecomputationfinished`).
- **Symptom:** the recomputation-forwarding helpers (`fulfillInvalid`, `fulfillRestoreOldValue`, `restoreOldValue`, `fulfillNull`, `getOldValue`, `forgetOldValue`, `isRecomputationfinished`, `hasOldValue`, `queryChangedDependencies`) are declared **non-static** on an otherwise all-static class with no public constructor/instance — so they're effectively uncallable. Almost certainly meant to be `static`. Also `isRecomputationfinished` is misspelled (lowercase `f`).
- **Confidence:** medium-high. **Impact:** dead/uncallable helper API.
- **Found:** `Recomputations` doc.

### PB-22 — `ListenerManager(boolean sorting)` drops its argument
- **Where:** `src/pile/aspect/listen/ListenValue.java` (the `ListenerManager` nested class).
- **Symptom:** the `ListenerManager(boolean sorting)` constructor never assigns `this.sorting`, so the argument is silently dropped and the field stays at its default `false`. `new ListenerManager(true)` therefore behaves like `new ListenerManager()` instead of enabling priority-sorted firing.
- **Confidence:** high (dropped constructor parameter). **Impact:** sorted listener firing can't be enabled via that constructor.
- **Found:** `ListenValue` doc.

### PB-23 — rate-limited `MultiListenValue` never propagates the collected sources
- **Where:** `src/pile/aspect/listen/ConcreteMultiListenValue.java`.
- **Symptom:** in `rateLimited(careAboutSources=true)` mode, the firing branch ignores the accumulated `MultiEvent` and fires `new ValueEvent(manager.getValueEventSource())`, so the collected upstream sources are never propagated — contradicting the `MultiListenValue.rateLimited` javadoc and the constructor javadoc. `careAboutSources` is effectively dead.
- **Confidence:** high. **Impact:** handlers in rate-limited multi-listen mode can't tell which value(s) changed.
- **Found:** `MultiListenValue` + `ConcreteMultiListenValue` docs.

### PB-24 — any-value bracket opened on the old value is never recorded (leaks)
- **Where:** `src/pile/impl/AbstractReadListenDependency.java` (`_addAnyValueBracket`, the `else` branch reached when `!valid && oldValid`).
- **Symptom:** that branch opens the bracket on the old value but guards `activeAnyBracketsOnOld.add(b)` with `if(valid)` — always false there — so the just-opened bracket is never recorded as active and is therefore never closed/transferred. Copy-paste slip from the `valid` branch; the guard should be `if(oldValid)`.
- **Confidence:** high. **Impact:** an any-value bracket added with `openNow` while only the old value is valid opens but leaks (its `close` never runs).
- **Found:** `HasBrackets` doc.

### PB-25 — `FilteredBracket.close` guards on the wrong filter
- **Where:** `src/pile/aspect/bracket/FilteredBracket.java`.
- **Symptom:** `close`'s null-guard tests `closeFilter != null` but then calls `openFilter.test(value)` (should guard on `openFilter != null`). So `nopOnNullClose` (openFilter==null, closeFilter!=null) throws NPE on close; `nopOnNullOpen` (closeFilter==null) skips the close gate so `back.close` runs even for values `open` filtered out, breaking symmetry. The symmetric factories (`filtered(filter)`, `nopOnNull`) are unaffected, masking it.
- **Confidence:** high. **Impact:** NPE / broken open-close symmetry for the asymmetric `nopOnNull{Open,Close}` factories.
- **Found:** `FilteredBracket` doc.

### PB-26 (suspicious) — deferred/queued bracket nop-metadata mixes up keep/remain
- **Where:** `src/pile/aspect/bracket/DeferredValueBracket.java` and `QueuedValueBracket.java`.
- **Symptom:** `openIsNop` returns `keep==null & !backDoesOpen` but `open`'s return is driven by `remain` (not `keep`); `closeIsNop` returns `remain==null & !backDoesClose` but `close`'s return is driven by `keep`. The keep/remain guards appear swapped, so the nop metadata can be wrong when exactly one of keep/remain is non-null. Duplicated across both twins (possibly intentional, but suspicious).
- **Confidence:** low-medium. **Impact:** the framework's nop-optimization may skip/keep a bracket incorrectly in edge cases.
- **Found:** `DeferredValueBracket` + `QueuedValueBracket` docs.

### PB-27 — `QueuedValueBracket.getDefaultQueue` has broken double-checked locking
- **Where:** `src/pile/aspect/bracket/QueuedValueBracket.java`.
- **Symptom:** the lazy default-queue init re-reads `local = defaultQueue` outside the lock, and inside the `synchronized` block never re-reads the field — so `if(local==null)` tests a stale value and concurrent callers can each create a queue (the assignment overwrites unconditionally). The double-check is non-functional.
- **Confidence:** high. **Impact:** under contention multiple default `SequentialQueue`s can be created, so brackets meant to share one queue split across queues and lose ordering.
- **Found:** `QueuedValueBracket` doc.

### PB-28 — `ReactiveSuppressionSwitcher.setSuppressed(ReadListenValue)` ignores its argument
- **Where:** `src/pile/aspect/suppress/ReactiveSuppressionSwitcher.java`.
- **Symptom:** `setSuppressed(ReadListenValue newState)` calls `super.setSuppressed(state)`, passing the inherited current `state` field instead of a value derived from `newState`. The collection-taking overloads correctly compute `boolean s = ReadValueBool.isTrue(newState)` and pass `s`. Copy-paste slip — this no-collection overload ignores the requested new state.
- **Confidence:** medium-high. **Impact:** that overload doesn't apply the requested suppression state.
- **Found:** `ReactiveSuppressionSwitcher` doc.

### PB-29 — `SynchronizingFilesBackedValue.STRING_CODEC` NPEs on every read/write
- **Where:** `src/pile/interop/preferences/SynchronizingFilesBackedValue.java`, the `STRING_CODEC` field (`FileCodec<String>`).
- **Symptom:** `encode` builds `new OutputStreamWriter(useThis, …)` and `decode` builds `new InputStreamReader(useThis, …)` using the raw parameter `useThis`, instead of the locals `os`/`is` it just computed as `useThis==null ? Files.newOutputStream/newInputStream(path) : useThis`. The class always invokes the codec with `useThis==null` (`codec.encode(currentValue, p, null)` in `_write`, `codec.decode(nf, null)` in `_read`), so the stream wrapper gets a `null` and NPEs on every real read/write through the built-in string codec. Copy-paste slip (`useThis` should be `os`/`is`).
- **Confidence:** high. **Impact:** the default string codec is unusable; any `SynchronizingFilesBackedValue` using `STRING_CODEC` (or `viaString`, if it shares the body) fails at first I/O.
- **Found:** `SynchronizingFilesBackedValue` doc.

### PB-30 — `SynchronizingFilesBackedValue.autoPoll` null-checks the wrong reference
- **Where:** `src/pile/interop/preferences/SynchronizingFilesBackedValue.java`, static `autoPoll(Runnable, BooleanSupplier, ScheduledExecutorService, long)`.
- **Symptom:** the scheduled task does `Runnable deref = pollfRef.get(); if (pollfRef == null) cancel; else deref.run();`. The null-check is on `pollfRef` (the never-null `WeakCleanupWithRunnable` holder) where it should be on `deref` (the weak referent). As written, once the poll runnable is GC'd, `deref` is null and `deref.run()` NPEs instead of self-cancelling; the intended cancellation branch is dead.
- **Confidence:** high. **Impact:** the auto-poll task cannot self-cancel on GC and throws once its runnable is collected.
- **Found:** `SynchronizingFilesBackedValue` doc.

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

### PB-33 — `SequentialQueue.isQueueWorkerThread` compares a `Thread` to a `Future`
- **Where:** `src/pile/utils/SequentialQueue.java`, `isQueueWorkerThread()`.
- **Symptom:** the method compares `Thread.currentThread()` against the `queueWorkerFuture` field (a `Future`, never a `Thread`), so it always returns `false`. The intent was clearly to compare against the `queueWorkerThread` field. (The `ExecutorWithRecentThread.getRecentThread()` path correctly uses `queueWorkerThread`.)
- **Confidence:** high. **Impact:** any "am I running on the queue's worker thread?" check is always false — reentrancy/affinity logic relying on it is defeated.
- **Found:** `ExecutorWithRecentThread` / `SequentialQueue` docs.

### PB-34 — `SequentialQueue.shutdownNow` NPEs if called before the first `enqueue`
- **Where:** `src/pile/utils/SequentialQueue.java`, `shutdownNow()`.
- **Symptom:** it does `new ArrayList<>(q)` then `q.clear()` with no `q == null` guard, unlike the sibling methods `clearQueue`/`trimQueue`/`isTerminated` which all null-check `q`. The queue field `q` is created lazily on first `enqueue`, so `shutdownNow()` on a never-used queue throws `NullPointerException`.
- **Confidence:** high. **Impact:** shutting down a freshly-created, never-enqueued `SequentialQueue` throws.
- **Found:** `SequentialQueue` doc.

### PB-35 — `BooleanGroup_Max1.afterChange` callback never fires on value changes
- **Where:** `src/pile/relation/BooleanGroup_Max1.java`, `add(...)` / the `afterChange` callback.
- **Symptom:** `callback.run()` (the `afterChange` hook) is placed inside the `if (cl == null) { … }` member-registration branch instead of in the per-member listener lambda. So it runs **once, at the time a member is first added**, and never again on subsequent `ValueEvent`s — contradicting the `afterChange` javadoc ("invoked when a ValueEvent happens on one of the group's items … run after the ValueEvent has been handled"). The siblings `BooleanGroup_Exactly1` and `BooleanGroup_Min1` correctly invoke the callback inside the listener body.
- **Confidence:** high (two independent reads; cross-sibling comparison). **Impact:** any code relying on `afterChange` to react to selection changes in a `Max1` group is silently never notified. Copy/placement slip.
- **Found:** `BooleanGroup_Max1` / `BooleanGroup_Exactly1` docs.

### PB-36 — `ImplSwitchableRelation.disable()` enables instead of disables on first suppressor
- **Where:** `src/pile/relation/ImplSwitchableRelation.java`, `disable()`.
- **Symptom:** when acquiring the **first** suppressor (`suppressors == 1`), the method reads `v = shouldBeEnabled.getValidOrThrow()` and does `if (v != null) setEnabled.accept(v)` — pushing the `shouldBeEnabled` value (i.e. `true` when the relation *should* be on) into `isEnabled`. But a suppressor is now held, so the relation must be **off**. The first-acquire branch never forces `false` for the suppressor it just added (only the already-had-a-suppressor `else` path no-ops, and the release callback correctly re-checks `suppressors == 0`). The parallel `sbeChanged` handler does `if (suppressors > 0) v = false;` — the missing mirror of that here is the defect.
- **Confidence:** medium-high. **Impact:** acquiring the first `disable()` suppressor while `shouldBeEnabled == true` momentarily flips `isEnabled` to `true` instead of `false`, so the relation briefly (or until the next event) keeps enforcing while supposedly suppressed.
- **Found:** `ImplSwitchableRelation` doc.

### PB-38 — `PileInt.addRO` drops the operand (constant-add is broken)
- **Where:** `src/pile/specialized_int/PileInt.java`, `addRO(ReadDependency, int)`.
- **Symptom:** the body is `op.mapToInt(o -> o==null ? null : +value)` — the unary `+value` ignores the operand `o`, so the result is the **constant `value`** rather than `o + value`. The correct sibling `addRW` uses `Bijection.define(o->o+value, o->o-value)`. **Verified by direct read.** It propagates: `add(ReadDependency,int)` → `addRO`; `subtractRO(op,int)` → `addRO(op,-value)`; `subtract(ReadDependency,int)` → `subtractRO`. So every *read-only* "value ± constant" on an int produces a constant instead of the shifted operand. (The `*RW` variants are correct.)
- **Confidence:** high (read confirmed). **Impact:** `PileInt.add`/`subtract` with a constant int and a read-only operand silently yield the wrong (constant) reactive value.
- **Found:** `specialized_int` index.

### PB-39 — `PileDouble.inverse{RW,RO}` compute negation instead of reciprocal
- **Where:** `src/pile/specialized_double/PileDouble.java`, `inverseRW(...)` and `inverseRO(...)`.
- **Symptom:** both are named/documented as the reciprocal `1/v` but their **recompute** fulfils `-v` (negation) — a copy-paste slip from the adjacent `negativeRW`/`negativeRO`. In `inverseRW` the *value* is wrong (negation) while the seal *write-back* sets `1/v`, so forward value and write-back also disagree; in `inverseRO` there is no write-back to mask it, so the result is simply always the negation, never the reciprocal.
- **Confidence:** medium-high (two subagent reads). **Impact:** `inverse` double values read as the negation; `inverseRW` additionally has an inconsistent write-back, so round-trips and displayed value diverge.
- **Found:** `specialized_double` index / `PileDouble` doc.

### PB-40 — `PileDouble.divideRW` is read-only despite its name/contract
- **Where:** `src/pile/specialized_double/PileDouble.java`, `divideRW(ReadWriteDependency<Double>, double)`.
- **Symptom:** it delegates to `multiplyRO(op, 1/value)`, returning a **read-only** wrapper, despite the `RW` name and javadoc promising write-back to the operand. No `Bijection`/write-back is installed (contrast the correct `multiplyRW`/`addRW`, which use `Bijection.define`).
- **Confidence:** medium-high (subagent read). **Impact:** writes to a `divideRW` value are not redirected to the operand as documented.
- **Found:** `specialized_double` index.

### PB-41 — `PileString.RightmostFulfilling.NOT_NULL` is wrongly a `LeftmostFulfilling`
- **Where:** `src/pile/specialized_String/PileString.java`, the inner class `RightmostFulfilling`, static field `NOT_NULL`.
- **Symptom:** the field is declared as type `LeftmostFulfilling` and constructed as `new LeftmostFulfilling(...)` — a copy-paste slip from the sibling `LeftmostFulfilling.NOT_NULL`; it should be a `RightmostFulfilling`. The instance method `RightmostFulfilling.apply` is correct (right-biased), so only the (apparently unused) static constant is wrong.
- **Confidence:** medium-high (subagent read). **Impact:** low — the constant appears unused; left-biased if anyone does use it.
- **Found:** `specialized_String` index.

> Minor (not logged as PB): `ReadDependencyInt.times(int)` javadoc says it delegates to a non-existent `PileInt#multiplyRO` (body correctly calls `PileInt.multiply`); stale `@link`. Noted in the `specialized_int` doc as a wart.

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
- **PB-37 (dismissed — verified by reading, not a bug)** — `AbstractRelation.installEnabledListener` calling `getListener().runImmediately()` (no-arg) where subclasses use `runImmediately(true)`. **Verified against `ValueListener.runImmediately`:** the boolean is `inThisThread` (run synchronously on the caller's thread vs. asynchronously via `StandardExecutors.unlimited()`) — **not** an "initial pass" toggle. Both overloads fire the listener with a `null` event, so the base path *does* re-assert the invariant on re-enable; it just runs it on another thread. The originally-feared "won't re-equalize until next operand change" does not hold. (Residual nuance, not a bug: the async re-assert is unordered relative to the enable; flag only if a future ordering issue surfaces.) (Verified by reading, 2026-06-12.)
- **PB-18 (not a bug)** — writable `field`/`deref` "dropping" a write when there is no inner value. **Not a bug:** silently ignoring an unsupported write is idiomatic in Pile (cf. `Constant`; a field with no inner value has nowhere to write). The inline `//TODO` is about a *different* concern — field↔inner-value sync (see author-flagged). (Developer, 2026-06-12.)
