# `Nonreentrant`

A per-thread guard that prevents re-entrant execution of a section: if this thread is already inside the scope, the *fail* branch runs instead of the *action* (re-entry is **redirected**, never deferred or auto-thrown).

Source folder: `src` (package `pile.utils`).

Up: [utils index](_index.md) · [overview](../../overview.md). Sibling: [SequentialQueue.md](SequentialQueue.md).

## What it is for

`Nonreentrant` wraps an action so that, while a thread is running that action, any *re-entrant* call from the same thread takes a different path. The classic use is breaking feedback loops: a write handler that itself triggers writes, or two values kept in sync, where the propagation must not recurse back into the originating section.

The re-entry decision is **per thread**, held in a `ThreadLocal<Boolean>` field `in`. There is no global lock — two different threads run the *action* branch independently and concurrently; the guard only blocks a thread from re-entering its *own* in-flight section.

## The run API

Every entry method takes two callbacks of the same shape — `action` (run normally) and `fail` (run if this thread is already inside) — and follows the same try/finally pattern: read the prior `in` value, on re-entry run `fail` and return, otherwise set `in=TRUE`, run `action`, and in `finally` **restore the prior value** (not unconditionally clear it). Restoring rather than clearing means nesting is handled correctly even if a `Nonreentrant` is somehow entered through another mechanism.

- `get(Supplier action, Supplier fail)` — value-returning; returns `fail.get()` on re-entry.
- `apply(arg, Function action, Function fail)` — one argument, value-returning.
- `accept(arg, Consumer action, Consumer fail)` — one argument, void.
- `run(Runnable action, Runnable fail)` — void. **`fail` may be null** here (null is treated as a no-op on re-entry); the other three methods do not null-check their `fail`.
- `isIn()` — whether the current thread is currently inside this scope.

### `fixed(...)` — pre-bind the callbacks

The four overloaded `fixed(action, fail)` methods return a `Runnable` / `Supplier` / `Function` / `Consumer` that closes over `this` and the two callbacks, so the guarded section can be passed around and invoked later (e.g. installed as a listener). Each just defers to the matching entry method.

### `block()` / `block_noThrow()` — scoped (try-with-resources) form

These enter the scope for the lifetime of a returned `MockBlock` (closeable), instead of taking an `action`/`fail` pair:

- `block()` — **throws** `ReentrantException` if the thread is already inside; otherwise sets `in=TRUE` and returns a `MockBlock` whose close resets `in` to `false`. This is the only API that signals re-entry by throwing.
- `block_noThrow()` — on re-entry does nothing and returns `MockBlock.NOP`; otherwise behaves like `block()`. **Check the return value** — if it came back `NOP` you are re-entrant and must take the alternative path yourself; the javadoc explicitly warns that ignoring it makes the call pointless.

`ReentrantException` is an inner class (`extends Exception`, checked) with a private constructor; `getScope()` returns the enclosing `Nonreentrant`.

## Re-entry behavior at a glance

| Path | Re-entry behavior |
|---|---|
| `get` / `apply` / `accept` | run the supplied `fail` callback (mandatory) |
| `run` | run `fail`, or do nothing if `fail` is null |
| `block()` | throw `ReentrantException` (checked) |
| `block_noThrow()` | return `MockBlock.NOP`, do nothing |

In every case re-entry is **detected and redirected immediately** — the re-entrant call never blocks waiting for the outer call, and the outer section is never re-run. There is no queue and no deferral.

## Caveats & gotchas

- **`block()`/`block_noThrow()` reset `in` to `false` on close, not to the prior value** (unlike the callback methods, which restore `isIn`). So a `block` nested inside an already-`TRUE` scope will, on close, leave `in=false` rather than `TRUE`. In practice these are used at the outermost entry, but mixing `block*` inside a callback-based section would clear the flag early. The callback methods (`get`/`apply`/`accept`/`run`) are the nesting-safe ones.
- The boolean is stored as a `ThreadLocal<Boolean>` that is **never removed** — a thread that has ever entered keeps a `FALSE`/`null` entry. Harmless, but worth knowing for thread-pool hygiene.
- `block_noThrow` returning `MockBlock.NOP` is a silent signal, not an exception — easy to forget to check.

## Who uses it

Within Pile, the guard backs: `NonreentrantBracket` (`pile.aspect.bracket`, a `ValueBracket` variant — see `ValueBracket`/`ValueOnlyBracket`), `ReadWriteDependency` (`pile.aspect.combinations`), and `CoupleEqual` (`pile.relation`, for two-way equality coupling, the canonical "don't let the echo recurse" case). Resolve current call sites with the language server's find-references on the relevant method.

## Tech debt / warts

- Two distinct close semantics (restore-prior in the callback methods vs reset-to-`false` in `block*`) is an inconsistency that could bite when the two styles are nested. Documented above as a gotcha.
- `fail` is null-tolerant only in `run`, not in `get`/`apply`/`accept`; the asymmetry is undocumented in the javadoc.
