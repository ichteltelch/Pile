# `LastValueRemembererInt`

Pure narrowing of [`LastValueRememberer`](../../aspect/LastValueRememberer.md) to `Integer`; adds no new members.

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `LastValueRememberer<Integer>` directly (no Comparable intermediary). The entire interface body is empty; all behaviour (remembering the last non-null value across invalidation cycles) is inherited from the generic. This is the integer specialization used when a `LastValueRememberer<Integer>` is required without an explicit cast.

See [combinations index](_index.md) · [overview](../../../overview.md).
