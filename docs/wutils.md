# WUtils Libraries

Nearly everything shared between these plugins — the step pipeline, config generation,
loadables, i18n, configurables, schedulers, structures — comes from
**[WUtils](https://github.com/Wyne10/WUtils)**, a set of independently published
Java/Kotlin libraries under the Maven group `io.github.wyne10`. It is a library collection,
not a framework: modules are added one at a time and nothing is installed on the server.

**Read the WUtils wiki before writing new plumbing.** Most "I need a helper for this" cases
are already solved there:

- [User wiki](https://github.com/Wyne10/WUtils/blob/master/docs/user/AGENTS.md) — how to use
  a module, with examples and the traps worth knowing about. This is the one you want.
- [Contributor wiki](https://github.com/Wyne10/WUtils/blob/master/docs/dev/AGENTS.md) —
  internals, invariants and source citations, for changing WUtils itself.

## Modules used here

| Module | What these plugins use it for |
|---|---|
| `wutils-common` / `-common-kotlin` | `CompositeJavaPlugin` and steps, `Loadable`/`Loader`, `Terminable`, `Schedulers`, `EventRegistry`, config/item/player extensions |
| `wutils-config` | `@ConfigEntry` fields, config generation and reload |
| `wutils-configurables` | typed config values: items, durations, ranges, animations, GUI buttons, maps |
| `wutils-i18n` / `-i18n-kotlin` | language files, `I18n.global`, the `placeholderComponent`/`replace` extensions |
| `wutils-json` | `JsonRegistry` for small plugin-local state |
| `wutils-jdbc` | JDBC driver loading (used by ConnectionSource) |
| `wutils-animation` | timed particle/sound/firework sequences |
| `wutils-structure` | schematic-backed world structures, masks, regions, persistence |

Versions come from the shared catalog in `libs/` — see
[Build and Packaging](conventions/build.md).

## When WUtils has no answer

Add the helper to the plugin that needs it, in the feature package that uses it. If a second
plugin needs the same thing, that is the signal to propose it upstream in WUtils rather than
copying it — the family's duplication (`AbstractManager` copied into several plugins before
becoming this template's `LoadableProvider`) is a cost that has already been paid once.
