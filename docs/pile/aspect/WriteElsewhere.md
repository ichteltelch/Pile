# `WriteElsewhere`

Vestigial, **entirely commented-out** marker aspect — the whole file is a `//`-commented sketch of a "writes will happen elsewhere (another thread / later)" capability that was never activated; there is no live `WriteElsewhere` type today.

Source folder: `src` — `pile/aspect/WriteElsewhere.java`.

Up: [package index](_index.md) · [overview](../../overview.md).

## Status: inert / dead code

The file contains **no compiled code**. Every line, including the `package` declaration, is commented out. Consequences:

- There is **no `pile.aspect.WriteElsewhere` interface** on the classpath. Nothing can implement it, import it, or reference it.
- Confirmed by the language server: searching the indexed source for either the type `WriteElsewhere` or its sole method `wouldDefer` returns **zero symbols** and zero references. It is not wired into any value, aspect, or builder.

Document it as **aspirational / abandoned scaffolding**, not as a working feature.

## What it was *intended* to be

From the commented-out sketch, the design intent was:

> A **marker interface** indicating that write operations will be executed in another thread, or later on the same thread, under certain circumstances.

```java
public interface WriteElsewhere {
    public boolean wouldDefer;
}
```

So the idea was a one-method aspect: a value would advertise — via `wouldDefer` — whether a write submitted to it right now would be **deferred** (run asynchronously on another thread, or queued for later on the current thread) rather than applied synchronously. Callers could then branch on that (e.g. avoid assuming the post-`set` value is already in effect). This complements, but is distinct from, the existing write paths:

- [`WriteValue`](WriteValue.md) — the live write aspect. Its `set` returns the *actually-set* value (after correction/veto/redirect) and has **no defer hook**; there is no analog of `wouldDefer` in the shipped API.
- [`Sealable`](Sealable.md) — the live mechanism by which a write can be **redirected** elsewhere (the seal-mode that forwards writes to another target). Redirection there is about *where* the write goes, not *when*/*on which thread* — a different concern from the deferral this marker contemplated.

## Why it's here (best inference)

The name and javadoc place it in the same conceptual neighborhood as sealing/redirection ("write goes somewhere other than straight into this value"), and the project README/overview note that threaded and delayed recomputation exist in builders ([overview](../../overview.md): "threaded/delayed recomputation"). A `wouldDefer` query would have let client code detect that a `set` against such an async/delayed value won't take effect immediately. The feature was evidently never needed strongly enough to activate, and the concern it addressed is partly covered ad hoc elsewhere (async executors in `pile.interop.exec`, redirection in `Sealable`).

## Caveats & gotchas

- **Do not cite this as an existing capability.** If you need "is this write synchronous?" semantics, there is currently no framework answer — you would be *adding* one, effectively reviving this interface.
- If reviving: it is a clean marker (`extends` nothing, one boolean method) and could be mixed into the aspect combinations like the other `pile.aspect` markers — but you'd need to actually implement `wouldDefer` in `PileImpl`/builders and decide its contract (does it answer for the *current* call site, or in general?). The sketch leaves that ambiguous.

## Tech debt / warts

- A commented-out source file is itself a wart: it pollutes the package, is invisible to tooling (no symbol, no Javadoc), yet looks like a real unit in directory listings. Either implement it or delete it. The sibling index already flags it as doc-pending/marker only ([`_index.md`](_index.md)).
