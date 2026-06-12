# `IListenValueBuilder`

Capability builder interface for attaching `ValueListener`s to a [`ListenValue`](../aspect/...) at build time (with strong/weak and value-in-scope variants).

Source folder: `src` · package `pile.builder` · file `IListenValueBuilder.java`.

Up: [builder index](_index.md) · [root `IBuilder`](IBuilder.md) · [overview](../../overview.md).

## What it's for

`IListenValueBuilder<Self, V extends ListenValue>` is one of the granular **capability** sub-interfaces of [`IBuilder`](IBuilder.md) (alongside `ICorrigibleBuilder` / `ISealableBuilder`). It adds the single concern of **registering change listeners on the value while it is being built**, so you can wire up `onChange` callbacks fluently inside a `Piles.…` chain instead of calling `addValueListener` on the value after `build`.

It declares exactly one abstract method (`onChange`); everything else is a `default` convenience layered on top of it.

## Who inherits it

Two target interfaces extend it, so every concrete builder gets these methods:
- [`IPileBuilder`](IPileBuilder.md) → `PileBuilder` / `SealPileBuilder`.
- [`IIndependentBuilder`](IIndependentBuilder.md) → `IndependentBuilder`.

So both `Pile`s and `Independent` leaf values support `onChange*` at build time. (`IPileBuilder` reaches `ListenValue` because `Pile` is a `ListenValue`; `IIndependentBuilder` because `Independent` is too.)

## Methods (delta over the javadoc)

The whole API is built from one primitive plus mechanical wrappers — don't read the javadoc method-by-method; the shape is:

- **`Self onChange(ValueListener l)`** — the one abstract method. Registers `l` so it runs when the value changes. *(The `_f` / `_weak` variants all funnel through this.)*
- **`onChange_f(Function<? super V, ? extends ValueListener> l)`** — value-in-scope variant: calls your function with `valueBeingBuilt` so the listener can close over the value itself, then registers the result. Same `*_f` convention used by the bracket builders.
- **`onChange_weak(ValueListener l)`** — wraps `l` in a [`WeakValueListener`](../aspect/...) before registering, so the value holds no strong reference to your listener; it survives only as long as `l` is strongly reachable elsewhere.
- **`onChange_weak_f(Function l)`** — `_f` + weak combined.
- **`onChange_weak(ValueListener l, BiConsumer out)`** — weak registration that *hands back the two references you need*: `out.accept(weak, l)` is called with `(reference-to-pass-to-removeValueListener, reference-to-keep-strongly-reachable)`. Use this when you want to manage the listener's lifetime/removal manually. (See "Common tasks".)
- **`onChange_weak_f(Function l, BiConsumer out)`** — `_f` + the two-arg weak form.

## Override map

`onChange` is implemented **identically** in both abstract bases — each just forwards to the mid-build value:
- `AbstractPileBuilder.onChange`: `value.addValueListener(l); return self;`
- `AbstractIndependentBuilder.onChange`: `value.addValueListener(l); return self;`

`AbstractPileBuilder` also re-declares `onChange_weak_f(Function, BiConsumer)` but the body is identical to the interface default (`return onChange_weak(l.apply(valueBeingBuilt), out);`) — a redundant override, no behavior change.

All the other `default`s are used as-is (no overrides found).

### Key consequence: listeners attach *immediately*, not at `build`

Because `onChange` calls `value.addValueListener(l)` on `valueBeingBuilt` during configuration, the listener is **live on the partially-built value before `build` runs** — unlike, say, `Independent`'s initial value, which is deliberately deferred to `build` time so brackets/corrections apply (see [`IIndependentBuilder`](IIndependentBuilder.md)). If the build process itself causes value changes (recompute wiring, initial set, sealing), an already-registered `onChange` listener can fire **during** the build. Treat build-time listeners as already armed.

## Deferral — two distinct mechanisms (don't conflate)

There are two unrelated "defer" notions in play; the task hint about `IBuilder.deferListeners` touches only the first:

1. **`IBuilder.deferListeners(boolean)`** (see [`IBuilder`](IBuilder.md)) — a *per-value* flag set at build time via `value._setDeferringListeners(b)`. It controls whether **that value** batches/defers its listener notifications. It is orthogonal to *this* interface: `IListenValueBuilder` only adds the listeners; whether their notifications are deferred is governed by `deferListeners`. The two compose (add listeners with `onChange*`, then `deferListeners` to make their firing deferred) but neither calls the other.

2. **`ListenValue.DEFER`** — a `static final` thread-local `Deferrer` (FiFo queue). This is the framework-wide event-deferral queue used at *fire* time, independent of any builder. Not configured through this interface at all; noted only so the two "DEFER"s aren't confused.

## Caveats & gotchas

- **Weak listeners need an outside strong reference.** `onChange_weak(l)` keeps no strong ref to `l` from the value; if nothing else holds `l`, it can be collected and silently stop firing. Use the `onChange_weak(l, out)` form when you need to capture the reference to keep alive / to remove later.
- **`onChange_weak` constructs its own `WeakValueListener`.** Unlike `ListenValue.addWeakValueListener(l)` (which builds the wrapper *and* returns it), the builder's plain `onChange_weak(l)` discards the wrapper — so you cannot later remove that listener unless you used the two-arg `out` form to capture it.
- **Removal reference is the wrapper, not your listener.** For weak registrations, the object to pass to `ListenValue.removeValueListener(...)` is the `WeakValueListener` wrapper (first arg of `out`), not your original `l`.
- **`*_f` runs your function during configuration**, against `valueBeingBuilt` — a not-yet-finished value. Don't read its content there expecting the final built state.
- Listeners are live before `build` finishes — see the override map note above.

## The `pile.aspect.listen` package (forward reference)

`ValueListener`, `WeakValueListener`, and `ListenValue` itself live in `pile.aspect.listen` (source folder `src`), which is **not yet documented**. In brief: `ListenValue` is the read-side capability "this value can be listened to" (`addValueListener` / `removeValueListener` / `fireValueChange`, plus an inner `ListenerManager` that most implementations delegate to via the `ListenValue.Managed` sub-interface). `ValueListener` is the callback; `WeakValueListener` is a wrapper that lets the value reference the listener weakly. When that package is documented, point the `../aspect/...` links here at it.

## Common tasks

- **Attach a change listener at build time:** `Piles.….onChange(ev -> …).build`.
- **Listener that needs the value itself:** `.onChange_f(v -> ev -> useBoth(v, ev))`.
- **Attach without pinning the listener (weak):** `.onChange_weak(myListener)` — but keep `myListener` strongly reachable yourself, or it stops firing.
- **Weak + later removal:** `.onChange_weak(myListener, (wrapper, strong) -> { keepAlive = strong; toRemove = wrapper; })`, then later `value.removeValueListener(toRemove)`.
- **Defer the firing of those listeners on this value:** combine with [`IBuilder`](IBuilder.md)'s `.deferListeners`.

## Tech debt / warts

- `AbstractPileBuilder.onChange_weak_f(Function, BiConsumer)` is a redundant override duplicating the interface default verbatim — removable.
- The plain `onChange_weak(l)` quietly drops the `WeakValueListener` wrapper, making the registration un-removable; the two-arg form exists precisely to recover it. Easy to pick the wrong overload.
- The two distinct "DEFER" concepts (`deferListeners` flag vs. `ListenValue.DEFER` thread-local) share a confusing name surface.
