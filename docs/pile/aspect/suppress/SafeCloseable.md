# `SafeCloseable`

An `AutoCloseable` whose `close` declares **no checked exception**, so it drops cleanly into try-with-resources.

Source folder: `src` — package `pile.aspect.suppress`.

## What it's for

`SafeCloseable` exists purely to narrow `AutoCloseable.close` so it throws nothing checked. Try-with-resources over a plain `AutoCloseable` forces the surrounding block to handle/declare `Exception`; over a `SafeCloseable` it doesn't. It is a `@FunctionalInterface`, so a no-arg cleanup lambda satisfies it.

## Members

- **`close`** — the sole abstract method; overrides `AutoCloseable.close` to declare no checked exception.
- **`NOP`** — shared do-nothing instance (`()->{}`). Use instead of `null` to avoid null-checks.

## Who extends it

It is the closeable base of the suppression layer:

- [`Suppressor`](Suppressor.md) — the release-to-undo handle; its `close` calls `release`.
- [`MockBlock`](MockBlock.md) — the scope object returned by the framework's `with*` thread-local overrides.

See the package [`_index.md`](_index.md). Up: [overview](../../../overview.md).

## Caveats

- No defaults beyond `NOP`; this is a one-method marker-style interface, nothing more.
