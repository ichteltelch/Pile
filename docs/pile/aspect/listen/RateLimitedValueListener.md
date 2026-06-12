# `RateLimitedValueListener`

A `ValueListener` wrapper that runs its handler at most once per cooldown window, accumulating the source values that changed in between runs so you can react to all of them at once but only react to those that actually changed.

Source folder: `src` · package `pile.aspect.listen`.

This is the "rate-limited observation of multiple values" feature from the project `README.md`. It is an `abstract` class implementing [`ValueListener`](ValueListener.md): each incoming [`ValueEvent`](ValueEvent.md) is recorded and (re)schedules a future run of the handler on a [`ScheduledExecutorService`](../../interop/exec/StandardExecutors.md) rather than running it inline. See the package [`_index.md`](_index.md) and the [overview](../../../overview.md).

## What it's for

You have one or more reactive values firing changes faster than you want to handle them (e.g. driving an expensive recompute, a repaint, a save). Register a `RateLimitedValueListener` on each: it coalesces the flood into a paced sequence of handler runs, and (optionally) hands the handler a `MultiEvent` (see below) — the *set of all source values* that changed since the last run — so the handler can process them in a batch yet still distinguish which ones moved (the `MultiEvent`, described below).

Contrast with a plain listener (fires synchronously, once per event) and with `async`/`inQueue` wrappers on [`ValueListener`](ValueListener.md) (move execution off-thread but do **not** rate-limit or coalesce).

## Who creates it

- **`ValueListener.rateLimited(coldStartTime, coolDownTime, [startCoolingBefore,] run)`** — four overloads (`Consumer<? super MultiEvent>` or `Runnable`; with or without `startCoolingBefore`, which defaults to `false`). All delegate to `RateLimitedValueListener.wrap(...)` and **always pass `allowParallel=false`**. So via the public factory the handler is never re-entered concurrently.
- **`RateLimitedValueListener.wrap(coldStartTime, coolDownTime, startCoolingBefore, allowParallel, run)`** — the static factories that actually build an anonymous subclass. Two overloads: a `Consumer<? super MultiEvent>` (constructs with `produceMultiEvents=true`) and a `Runnable` (`produceMultiEvents=false`, since a `Runnable` has nowhere to receive the event). These are the only way to set `allowParallel=true`.
- Subclass directly and implement `protected void doRun(MultiEvent e) throws InterruptedException` if you want fields/state.

Note: the `Piles.rateLimited` mentioned in the [overview](../../../overview.md) is a *different* feature (rate-limited recompute of a derived value); it is not this listener.

## Construction parameters

`RateLimitedValueListener(coldStartTime, coolDownTime, startCoolingBefore, allowParallel, produceMultiEvents)`:

- **`coldStartTime`** (ms) — delay before the first/"cold" run when the handler has *not* run within the last `coolDownTime` ms. Lets you debounce a burst even when starting from idle.
- **`coolDownTime`** (ms) — minimum gap between successive handler invocations.
- **`startCoolingBefore`** — if `true`, the cooldown clock (`lastRun`) is stamped when the handler *starts*; if `false`, when it *finishes*. With long handlers this is the difference between "every N ms" and "N ms of idle between runs".
- **`allowParallel`** — whether a new run may be scheduled while one is still executing (see the `allowParallel` section below).
- **`produceMultiEvents`** — whether to accumulate sources. If `false`, `happened` stays `null` and `doRun` always receives `e == null` (an "all sources" event).

## The scheduling mechanism

`valueChanged(e)` and `multipleValuesChanged(collection)` share the same logic, all under `synchronized(this)`:

1. Record the source(s) into the `happened` set (if `produceMultiEvents`). A `null` event, or a `null`/null-containing collection, records `null` — the "all sources" marker.
2. Set `newEventsArrived = true`.
3. If more than `coolDownTime` has elapsed since `lastRun` → schedule a run in `coldStartTime` ms. Otherwise, if nothing is already scheduled → schedule one in the *remaining* cooldown (`coolDownTime - runAgo`) ms; if a run is already scheduled, do nothing (it will pick up the new sources).

`scheduleFutureRun(wait)` submits `runBody` to the executor via `exec.schedule(..., wait, MILLISECONDS)`. A negative `wait` (used by `runImmediately`/`refire` when there is no cooldown) bypasses the executor and runs the body **inline in the calling thread** with `itt=true`. `disable` also forces this inline path.

`runBody`: snapshots `happened` into a fresh `MultiEvent`, **clears `happened`**, clears `newEventsArrived`, stamps `lastRun` if cooling-before, then calls `doRun(e)` *outside* the lock. In `finally` it stamps `lastRun` if cooling-after and — if new events arrived while the handler ran — reschedules itself for the rest of the cooldown. This self-rescheduling tail is what keeps a steadily-firing source paced indefinitely.

## `MultiEvent` — the accumulated-sources event

A nested **inner** class (holds a reference to the enclosing listener). Wraps the `HashSet<Object>` of accumulated sources, or `null` for an "all sources" event.

- **`isSource(Object o)`** — was `o` among the sources? Returns `true` for *every* `o` when this is an all-sources event.
- **`allSources`** — `true` iff sources is `null` (an all-sources run, e.g. triggered by a `null` event or by `runImmediately`).
- **`getSources`** — the `HashSet` of sources, or `null` for all-sources. (Live snapshot taken at run time, already detached from the listener's working set.)
- **`anySource(Predicate)`** — any source matching the predicate. **Returns `false` for an all-sources event** — there is no concrete set to test, so a predicate-based filter silently sees nothing on those runs. Gotcha: combine with `allSources` if you need to treat "all" as a match.
- **`refire`** — call from inside the handler when it cannot proceed now but wants the same sources delivered again later. Re-invokes `multipleValuesChanged(sources)` — off the executor if there is a cooldown, inline if `coolDownTime < 0`.

Constructor caveat: if the incoming set is `null` *or contains `null`*, the whole event collapses to all-sources (`sources = null`, ) — a single `null` source "poisons" the batch into an all-sources run.

## Handler variants: `Consumer<MultiEvent>` vs `Runnable`

- **`Consumer<? super MultiEvent>`** (`wrap(...)` with `produceMultiEvents=true`): handler receives the populated `MultiEvent`. Use when you need to know *which* values changed.
- **`Runnable`** (`produceMultiEvents=false`): sources are never accumulated; `doRun` gets `e == null` every time. Use for "something changed, just refresh" handlers.

The `MultiEvent` passed to a `Consumer` can still be `null` only in the `produceMultiEvents=false` case; with a `Consumer` overload `produceMultiEvents` is hard-wired `true`, so the consumer always gets a non-null event (possibly an all-sources one).

## Which thread it fires on

The handler runs on the configured `ScheduledExecutorService` — by default `StandardExecutors.delayed`, set per-instance via **`setExecutor(exec)`** (passing `null` restores the default). It does **not** run on the thread that wrote the source value. Exceptions therefore surface on the executor, not the writer. Exception: the inline paths — `runImmediately`, `disable`, and a `refire` with `coolDownTime < 0` — run `doRun` synchronously on the *calling* thread.

`InterruptedException` from `doRun` is swallowed; `runBody`'s `finally` still runs (stamps `lastRun`, may reschedule).

## Running on demand

- **`runImmediately`** — records an all-sources marker and schedules with `wait = -1`, i.e. runs the body **inline in the current thread** right now. Used by `ValueListener.runImmediately(true)`.
- **`runImmediately(boolean inThisThread)`** — `true` calls the above inline; `false` hands `runImmediately` to `StandardExecutors.unlimited` to run on a worker thread.
- **`disable`** — debugging aid: sets `enabled=false` so every subsequent `scheduleFutureRun` runs the body inline in the firing thread (no rate limiting at all). Returns `this`. Named "break this listener" in the javadoc — only for diagnosing handler bugs.

## `allowParallel`

When `false` (the default and the only value the `ValueListener.rateLimited` factories use), `scheduledRun` is held until the run finishes, so a `scheduleFutureRun` call sees an in-flight run as "already scheduled" and refrains. When `true`, `runBody` clears `scheduledRun` *before* calling `doRun`, allowing the next window's run to start concurrently. Only reachable via `RateLimitedValueListener.wrap(..., allowParallel=true, ...)`.

## Caveats & gotchas

- **Not synchronous with the write.** Handler side effects happen later, on the executor, ordered only by the cooldown logic — not relative to the triggering write.
- **`anySource` ignores all-sources events** — see above. A `null` source anywhere in a batch turns the whole batch into all-sources, so predicate filters silently match nothing that run.
- **A single `null` source poisons the batch** into an all-sources `MultiEvent`. Intentional: `null` means "assume everything changed".
- **`refire` can loop** if the handler always refires without the blocking condition clearing — it just re-enqueues the same sources.
- **`Runnable` handlers can't see sources** — by design; don't reach for the sources there.
- **`disable` removes all rate limiting** and makes handling synchronous + reentrant; never leave it on in production.
- **`coolDownTime < 0`** is a supported sentinel that turns `refire` inline; the scheduling comparisons (`runAgo > coolDownTime`) then almost always take the cold-start branch.

## Tech debt / warts

- Leftover debug scaffolding: `System.out.println("what???")` in the dead `else` branch of `scheduleFutureRun`, plus several commented-out `println`/timing lines in `runBody`.
- `valueChanged` and `multipleValuesChanged` duplicate the same ~15-line scheduling block; the empty `else { }` blocks are intentional no-ops left explicit.
- `MultiEvent` is an inner (non-static) class, so each event pins the enclosing listener — fine here, but a sharper API could make it static and pass the listener explicitly.
- The `produceMultiEvents` flag and the `Consumer`/`Runnable` split encode the same intent twice; a `Runnable` wrap with `produceMultiEvents=true` would just waste the accumulated set.
