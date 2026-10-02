# Loadables and Definitions

`@ConfigEntry` covers single settings. Anything that is a *set of named things* — dungeons,
containers, effects, loot pools, custom items — is a **definition** loaded through
`Loadable`, and the plugin exposes it through a `LoadableProvider`.

## Loadable

Implement `me.wyne.wutils.common.loadable.Loadable`, register in `init`, and read from the
section handed to you:

```kotlin
init {
    Loader.global.registerLoadable(this)
}

override fun load(config: ConfigurationSection) { … }
```

`Loader.global.load(plugin)` runs one pass over every registration in ascending priority.
Use `@LoadableMeta(priority = n)` when one loadable must observe another's result: give a
service that reads from a repository priority 1 so the repository (default 0) has rebuilt its
DAOs first.

`load` is called on startup **and on every reload**, so it must be idempotent: clear
collections, stop tasks before rescheduling, despawn before respawning.

## LoadableProvider

`LoadableProvider.kt` (in this template; older plugins call it `AbstractManager`) is the
shared implementation of "load a map of `key -> V` from a config section and/or a
directory of YAML files":

```kotlin
class KitProvider : LoadableProvider<Kit>("kit", Kit.Factory) {
    init { PluginTemplate.instance.saveDirectoryResource(directory) }

    override fun load(config: ConfigurationSection) {
        super.load(config)
        loadFiles(directory)
    }
}
```

What it gives you:

- `map` — a read-only snapshot of loaded values; `get(key)` via the `[]` operator.
- `directory` — `<dataFolder>/<sectionKey>`, the convention for per-entity files.
- `load(config)` — reads `config.getConfigurationSection(sectionKey)`, one entry per key.
- `loadFiles(directory)` — recursive; nested directories become `sub/dir/name` keys,
  file name minus extension is the key.
- Per-key `runCatching` so one broken definition logs an error instead of aborting the pass.

Copy the bundled defaults out of the jar with `saveDirectoryResource` (the
`wutils-common-kotlin` extension `me.wyne.wutils.common.kotlin.config.saveDirectoryResource`)
so `src/main/resources/kit/*.yml` lands in the data folder on first run.

## GenericFactory

A definition is a `data class` with a `Factory` companion object implementing
`GenericFactory<V>`. The factory is the *only* place that touches `ConfigurationSection`:

```kotlin
data class Kit(
    val key: String,
    val cooldown: TimeSpan,
    val contents: LootPool
) {
    object Factory : GenericFactory<Kit> {
        override fun create(key: String, config: ConfigurationSection): Kit {
            val section = config.deep(key)
            …
        }
    }
}
```

`config.deep(key)` resolves "the subsection named `key`, or this section itself" — that is
what lets the same factory read both an inline `section:` entry in `config.yml` and a
standalone `<sectionKey>/<key>.yml` file.

## Definition and world instance

Where a definition can exist multiple times in the world, split it in two: the immutable
definition (`Kit`) and the live instance (`WorldKit`) that holds runtime state, listens to
events and can be despawned. Name the pair `X` and `WorldX`. The definition creates the
instance through a `wrap`/constructor call; the service owns the set of live instances and
reconciles it against the configured amount on load.
