# `pile.aspect.listen.ListenValue`

The aspect of a reactive value concerning its **observability**: register/unregister [`ValueListener`](ValueListener.md)s, fire change events, and the nested `Managed`/`ListenerManager` machinery plus the `DEFER` listener-deferral.

Source folder: `src`. File: `pile/aspect/listen/ListenValue.java`.

Up: [listen index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md). Listeners and events: [`ValueListener`](ValueListener.md), [`ValueEvent`](ValueEvent.md). The production implementation is wired into [`AbstractReadListenDependency`](../../impl/AbstractReadListenDependency.md) (ARLD). Deferral ties into [concepts/transactions.md](../../../concepts/transactions.md).

## What it's for

`ListenValue` is the interface a value implements so that code *outside* the dependency graph can watch it for changes. It is one of the granular aspect interfaces assembled into `ReadListenDependency` → `Pile`. Listeners registered here fire *imperatively* whenever the value announces a change via `fireValueChange` — this is distinct from (and downstream of) the dependency/recompute graph, which uses `Depender`/`Dependency` rather than `ValueListener`s.

## Add / remove / query API (the abstract contract)

The interface declares four abstract methods that any implementor must supply; everything else is a `default` built on top of them:

- `addValueListener(ValueListener)` — register a listener that runs **immediately, in the thread that fired the change**.
- `removeValueListener(ValueListener)` — unregister it.
- `hasValueListener(ValueListener)` — membership test.
- `fireValueChange` — notify all registered listeners (generates a fresh `ValueEvent`).
- `removeWeakValueListener(ValueListener)` — abstract too; remove a weakly-held listener by the *wrapped* original listener (see Weak listeners).

Convenience defaults layered on top:

- `addValueListener_(cl)` — adds the listener **and then calls `cl.runImmediately`**, i.e. fires it once right away with a `null` event. Use this when the listener should reflect the current value at registration time, not only on future changes. (Trailing underscore = "and run now".)
- `addValueListener(unregisterer, l)` / `addWeakValueListener(unregisterer, l)` — register through a [`ValueListenenerUnregisterer`](_index.md) (note the misspelled type name) that owns removal; the weak variant also auto-removes when the listener becomes weakly reachable.
- `addWeakValueListener(listener)` — wraps `listener` in a `WeakValueListener` and registers the wrapper, keeping **no strong reference** to the original. Returns the wrapper; pass *that* to `removeValueListener` for cheap removal, or pass the original to `removeWeakValueListener`. Caller must keep a reference to the original alive for as long as it should stay registered.
- `getValueEventSource` — the object reported as the event source; default is `this`.
- `isSource(MultiEvent)` — whether this value is among the sources of a rate-limited `MultiEvent`.

## `Managed` — the mixin that delegates to a `ListenerManager`

`ListenValue.Managed` is a nested interface a value class implements to avoid reimplementing listener bookkeeping. It supplies `default` overrides of all the abstract methods that simply forward to `_getListenerManager` — the one method the implementor must provide. This is the seam ARLD uses: **ARLD is `ListenValue.Managed`**, and its `_getListenerManager` lazily allocates a `ListenerManager` (see Where ARLD plugs in).

## `ListenerManager` — the default listener store

`ListenerManager` is a standalone `ListenValue` implementation holding the actual listener set. Key points:

- **Lazy set allocation:** `listeners` is `null` until the first add — observing nothing costs nothing.
- **`sorting` flag, confusingly named**: when `sorting == true` the set is a plain `HashSet` and listeners are sorted **at fire time** (`Arrays.sort` by `COMPARE_BY_PRIORITY`, ); when `false` the set is a `TreeSet` kept **in sorted order** by `COMPARE_BY_PRIORITY_AND_IDENTITY`. So `true` = "sort lazily on fire", `false` = "store pre-sorted". Either way listeners fire in priority order.
- **Add/remove fire lifecycle callbacks:** a successful add calls `l.youWereAdded(this)`; a successful remove calls `l.youWereRemoved(this)` — and these run **outside** the `synchronized` block.
- **`fireValueChange(ValueEvent)`**: snapshots the listener set into an array under the lock, then iterates **outside** the lock. Each `valueChanged` call is wrapped in try/catch — **a throwing listener is logged at `WARNING` and swallowed**, so one bad listener does not stop the others.
- **Re-entrancy guard:** `fireValueChange(ValueEvent)` throws `IllegalMonitorStateException` if the calling thread already holds the manager's lock — firing must not happen while holding the monitor.
- `source` (constructor arg) is what events report as their origin; defaults to the manager itself.

### `removeWeakValueListener`

Scans the listener set, asking each entry `asWeakValueListener`. It removes any `WeakValueListener` whose referent has been **collected** (`wl.get==null`) *or* equals the `wrapped` argument. So this both unregisters a specific weak listener and opportunistically reaps dead ones. Removed listeners get `youWereRemoved` outside the lock.

## The `DEFER` suppressor (and `Recomputations.NOT_NOW`)

`DEFER` is a `public static final Deferrer` — a thread-local FIFO deferral queue (`Deferrer.makeThreadLocal(DeferrerQueue.FiFo::new)`). A [`Deferrer`](../../utils/defer/Deferrer.md) decides whether a `Runnable` handed to its `run(...)` executes **immediately** or is **queued and replayed later**, controlled by `suppressRunningImmediately` (a `Suppressor`): while any suppressor is open the deferrer collects runnables; when the last one closes, the queued runnables run.

**What deferral means here:** when listener notifications are deferred, a `fireValueChange` does not invoke listeners synchronously — the notification is parked on `DEFER`'s queue and replayed once the deferral suppressor is released. This lets a burst of changes (e.g. a batch of writes inside a transaction) collapse/postpone observer callbacks until a consistent point, rather than firing observers mid-flux.

**`Recomputations.NOT_NOW` is the same object.** `Recomputations.java` declares `public static final Deferrer NOT_NOW = ListenValue.DEFER;` — it is a literal alias, not a parallel mechanism. Code that opens a `NOT_NOW` suppressor and code that opens a `ListenValue.DEFER` suppressor are deferring on the **one shared queue**. Treat the two names as interchangeable; `NOT_NOW` is just the name used in the recomputation subsystem.

`DEFER` is also used by the bracket machinery (`DeferredValueBracket`, `ValueBracket`, `ValueOnlyBracket`) to defer bracket bodies on the same queue.

## Where ARLD plugs in

[`AbstractReadListenDependency`](../../impl/AbstractReadListenDependency.md) implements `ListenValue.Managed`. Its `_getListenerManager` lazily allocates a `ListenerManager` **subclass** whose `fireValueChange(ValueEvent)` consults a per-instance `isDeferringListeners` flag: when set, it wraps the super call in `ListenValue.DEFER.run(...)`; otherwise it fires synchronously. So the deferral described above is engaged per-value by ARLD, and the actual store/iteration logic is the `ListenerManager` documented here. ARLD's public `fireValueChange` additionally short-circuits when destroyed or listener-less and wraps the fire in a `shouldDeepRevalidate` block.

## Caveats & gotchas

- **`sorting`'s sense is inverted from intuition** — `true` means "not stored sorted, sort on fire". Read the field doc at `ListenValue.java`, not the name.
- **Listener exceptions are swallowed** (logged at `WARNING`, ). A listener that throws will not propagate; don't rely on exceptions from `valueChanged` reaching the firer.
- **`addValueListener_` fires once with a `null` event** — listeners must tolerate a `null` `ValueEvent` (via `runImmediately`), not just real change events.
- **Weak listeners need a live strong reference** kept by the caller; otherwise they can be collected and silently auto-unregistered. Remove the *wrapper* (returned by `addWeakValueListener`) for cheap removal, or the original via `removeWeakValueListener`.
- **`DEFER` / `NOT_NOW` are thread-local** — deferral applies to the firing thread, and queued callbacks replay on the thread that releases the last suppressor.
- Firing must not hold the manager's monitor (`IllegalMonitorStateException`, ).

## Common tasks

- **Observe a value and reflect its current state immediately:** `v.addValueListener_(listener)`.
- **Observe without keeping the listener alive yourself indefinitely:** `ValueListener w = v.addWeakValueListener(listener)` and keep `listener` referenced; remove with `v.removeValueListener(w)`.
- **Batch/postpone observer callbacks across a burst of changes:** open a `ListenValue.DEFER.suppressRunningImmediately` (equivalently `Recomputations.NOT_NOW`) suppressor around the changes; callbacks replay when it closes.
- **Implement observability on a new value class:** implement `ListenValue.Managed` and return a `ListenerManager` from `_getListenerManager`.

## Tech debt / warts

- The `sorting` field name is misleading (documented inversion).
- Type name `ValueListenenerUnregisterer` is misspelled (carried through the API).
- `ListenerManager(boolean sorting)` constructor sets `source=this` but **never assigns `this.sorting`** — the `sorting` argument is dropped and the field keeps the boolean default `false`. So `new ListenerManager(true)` does *not* behave like the default `new ListenerManager` (which is `sorting=true`). The other `sorting`-taking constructor does honor it. (Flagged as a suspected bug.)
- `fireValueChange(ValueEvent)` re-checks `listeners==null || isEmpty` twice in a row — harmless dead double-check.
- A commented-out `DEFER = Deferrer.DONT` alternative is left in place as a toggle for disabling deferral.
