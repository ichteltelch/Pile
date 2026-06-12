# `ValueListener`

The observer callback interface: its `valueChanged(ValueEvent)` runs when a [`ListenValue`](ListenValue.md) it is registered on changes.

Source folder: `src` · package `pile.aspect.listen`.

A `@FunctionalInterface` — a `ValueListener` is essentially a `Consumer<ValueEvent>` with a priority and add/remove lifecycle hooks. It is the third member of the observation triad: a [`ListenValue`](ListenValue.md) holds a set of `ValueListener`s and notifies each with a [`ValueEvent`](ValueEvent.md) when it changes. See the package [`_index.md`](_index.md) and the [overview](../../../overview.md).

## The callback

- **`void valueChanged(ValueEvent e)`** — the single abstract method; your handler goes here. Everything else on the interface is `default`/`static`. Because it is functional, the common way to make one is a lambda `e -> { ... }`.
- The event `e` **may be `null`** — `runImmediately(...)` deliberately calls `valueChanged(null)` to fire the handler once with no originating event. Handlers that dereference `e` must null-check, or they will NPE when invoked this way.

## Priority and ordering

- **`int priority`** (default `0`) — smaller priorities run sooner. The contract states *the priority must not change* over the listener's lifetime; the `ListenValue` may have ordered listeners by it.
- **`COMPARE_BY_PRIORITY`** / **`COMPARE_BY_PRIORITY_AND_IDENTITY`** — comparators for sorting listeners. The identity variant breaks priority ties via `IdentityComparator.INST` so distinct equal-priority listeners get a stable, total order (needed for use as set/map keys without collapsing).
- **`withPrio(int)`** / static **`withPrio(int, ValueListener)`** — wrap *this* handler in a [`PrioWrappedValueListener`](PrioWrappedValueListener.md) carrying a different priority. The handler code is unchanged; only `priority` differs.

## Lifecycle hooks

- **`youWereAdded(ListenValue toThis)`** / **`youWereRemoved(ListenValue fromThis)`** — empty defaults; called by a `ListenValue` when this listener is registered / unregistered. Wrappers that need to track their host override these — e.g. [`WeakValueListener`](WeakValueListener.md) uses them to remember which `ListenValue`s it must auto-unregister from when its referent is collected.
- **`WeakValueListener asWeakValueListener`** — returns `null` by default; `WeakValueListener` overrides it to return itself. A cheap `instanceof`-style downcast the registration machinery uses to special-case weak listeners.

## Wrapper / decorator family

All of these return a *new* `ValueListener` (or a specific subtype) wrapping a handler; the original is unchanged.

- **`rateLimited(coldStartTime, coolDownTime, [startCoolingBefore,] run)`** — four overloads producing a [`RateLimitedValueListener`](RateLimitedValueListener.md): the handler fires at most once per cooldown window. The `Consumer<? super MultiEvent>` overloads pass the **accumulated set of source values** that fired in between runs; the `Runnable` overloads ignore the sources. The 3-arg overloads default `startCoolingBefore` to `false`. (All delegate to `RateLimitedValueListener.wrap(...)` with `allowParallel=false`.)
- **`async(l)`** / **`async(ExecutorService, l)`** — wrap so the handler runs on an executor (default `StandardExecutors.unlimited`) instead of the firing thread.
- **`inQueue(SequentialQueue, l)`** — wrap so each event is enqueued on a `SequentialQueue` (serialized, ordered, off the firing thread).
- **`ignoreTransformEvents`** / static `ignoreTransformEvents(self)` — wrap in a [`TransformValueEventIgnoringValueListener`](TransformValueEventIgnoringValueListener.md) so `TransformValueEvent`s are dropped and only "real" value changes reach the handler.

## Running on demand

- **`runImmediately`** / **`runImmediately(boolean inThisThread)`** — invoke the handler now with a `null` event. The no-arg form (and `inThisThread=false`) runs it on `StandardExecutors.unlimited` in another thread; `inThisThread=true` runs it synchronously. Useful to do the initial computation a listener would normally only do on the first change.

## Caveats & gotchas

- **Null event in handlers.** As above, `valueChanged` can receive `null` (via `runImmediately`). Always null-check `e` if you read it.
- **Priority must be stable.** Returning a varying `priority` violates the contract and can corrupt the listener ordering inside a `ListenValue`.
- **`async`/`inQueue` move execution off the firing thread**, so handler side effects no longer happen synchronously with the change — ordering relative to the triggering write is not guaranteed, and exceptions surface on the executor/queue, not the writer.
- The static `withPrio(int, ValueListener)` javadoc speaks of "the same handler code as `vl`"; it simply delegates to `vl.withPrio(prio)`. The instance-method javadoc is the canonical one.

## Tech debt / warts

- The interface mixes three concerns — the callback, a priority/ordering scheme, and a large grab-bag of decorator factories — on one functional interface. Convenient, but it makes the type heavy for what is conceptually a `Consumer<ValueEvent>`.
- `youWereRemoved`'s body is `{};` with a stray trailing semicolon — harmless.
