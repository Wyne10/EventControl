# Plugin Anatomy

Every plugin built from this template has the same skeleton. This page names the parts and
where they live, so you can find your way around an unfamiliar plugin in the family without
reading it end to end.

## Source layout

```
src/main/kotlin/org/bigcraft/<plugin>/
    <Plugin>.kt          main class, extends CompositeJavaPlugin
    PluginSteps.kt       every startup/reload/shutdown step, in one file
    LoadableProvider.kt  generic "load keyed values from config/files" helper
    module/              Guice modules: PluginModule, CoreModules, OptionalModules
    command/             <Plugin>Command.kt (root) + SubCommands.kt (leaves)
    placeholder/         PlaceholderAPI expansions
    menu/ or gui/        AbstractMenus catalogs, or InvUI/triumph-gui screens
    <feature>/           one package per domain area
src/main/resources/
    config.yml           the bundled default config
    lang/ru.yml          the bundled default language
    <section>/*.yml      bundled definition files, one per keyed entity
```

Feature packages are named after the domain, not after layers — `kit/`, `region/`,
`treasury/`, each holding its definitions, services and listeners together. There is no
`impl/`, and `util/` is only for genuine leftovers such as version-bridging adapters.

## The main class

The main class does almost nothing. It extends `CompositeJavaPlugin<Self>`, sets a
`companion object` singleton, and lists its steps:

```kotlin
class PluginTemplate : CompositeJavaPlugin<PluginTemplate>() {

    lateinit var injector: Injector
        internal set

    override fun init() {
        instance = this
        addSteps(LoadDefaultConfig, InitializeLogger, /* ... */ Load, Reload)
    }

    companion object {
        lateinit var instance: PluginTemplate
            private set
        lateinit var logger: Logger
            internal set
        val EMPTY_CONFIGURATION = MemoryConfiguration()
    }
}
```

Three companion members appear in every plugin and are the ones other files reach for:

- `instance` — the plugin, for `dataFolder`, `config`, listener registration.
- `logger` — an SLF4J `Logger` wrapped by [`LoggerWrapper`](conventions/lifecycle.md) so
  the config's `logLevel` is respected. **Never use `plugin.logger` (`java.util.logging`)
  or `println`.**
- `EMPTY_CONFIGURATION` — an empty `MemoryConfiguration` installed as the config's defaults
  so Bukkit's own bundled-resource defaults never mask a value the owner deleted.

`injector` is assigned by the `InitializeInjector` step and is `internal set`.

## Where behaviour lives

Classes are `@Singleton` and constructor-injected; a class is created because a Guice
module binds it, not because someone called its constructor. A typical service class
combines three roles:

- `Loadable` — it reads itself from config on startup and on reload.
- `Terminable` — it releases tasks/listeners on shutdown, bound with `bindWith(plugin)`
  or `plugin.bind(this)`.
- `Listener` — Bukkit events, registered in `init`.

A service that spawns something into the world usually carries all three at once: it loads
its definitions, registers listeners for interaction with them, schedules an upkeep task,
and despawns everything in `close()`.

## See also

- [Startup, Reload and Shutdown](conventions/lifecycle.md)
- [Dependency Injection](conventions/dependency-injection.md)
- [Configuration](conventions/configuration.md)
