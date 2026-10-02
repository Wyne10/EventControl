# Startup, Reload and Shutdown

Plugins do not put logic in `onEnable`. They declare ordered steps, and WUtils'
`CompositeJavaPlugin` runs them for the `ENABLE`, `DISABLE` and `RELOAD` scopes. This page
covers the canonical step list, what may be added to it, and how work is torn down.

## The canonical step list

`PluginSteps.kt` holds every step as an `object` (or a small class when it needs
constructor arguments), annotated with `@Step(priority = n, scope = StepScope.ENABLE)`.
The template's order is fixed, and plugins only ever insert steps into it:

| Priority | Step | What it does |
|---|---|---|
| 0 | `LoadDefaultConfig` | `saveDefaultConfig()`, then installs `EMPTY_CONFIGURATION` as defaults |
| 1 | `InitializeLogger` | builds `LoggerWrapper(slF4JLogger, LevelWrapper.valueOf(logLevel))` |
| 2 | `InitializeJson` | `JsonRegistry.global` — only in plugins that store JSON |
| 3 | `InitializeI18n` | builds `I18n.global` from `serializer`, `usePlayerLanguage`, `lang/ru.yml` |
| 4 | `InitializeInjector` | `Guice.createInjector(Stage.PRODUCTION, …)`; this is where singletons are constructed |
| 5 | `InitializeConfig` | `Config.global` generator + first `reloadConfig` |
| 6 | `InitializeLoader` | `Loader.global.registerConfig(Loader.DEFAULT_PATH, plugin.config)` |
| 7 | `Load` | `JsonRegistry.global.load()` and `Loader.global.load(plugin)` |

Then two composite steps:

```kotlin
@Step(scope = StepScope.DISABLE)
object Disable : CompositeStep<PluginTemplate>(Write)

@Step(scope = StepScope.RELOAD)
object Reload : CompositeStep<PluginTemplate>(ReloadConfig, InitializeLogger, InitializeLoader, InitializeI18n, Write, Load)
```

Note what `Reload` deliberately omits: the injector is **never** rebuilt. Reload re-reads
config and re-runs loadables; it does not recreate services. Anything that must survive a
reload (open GUIs, live entities, scheduled tasks) is the responsibility of the service's
own `load` method: a service owning live world state despawns what it spawned and
reconciles against the freshly read config there, rather than assuming it was just
constructed.

Steps that appear in more than one scope (`ReloadConfig`, `Write`) are plain objects with
no `@Step` annotation, referenced from the composites.

## Adding a step

Insert it at the right priority and add it to `addSteps(...)` in the main class — both are
required; the annotation alone does not register an `object` step. Never register a step as
a lambda: a lambda cannot carry `@Step` and silently becomes `ENABLE`/priority 0.

## Logging

`LoggerWrapper` gates an SLF4J logger on the config's `logLevel`. Use the plugin
companion's `logger` and SLF4J's `{}` placeholders, with the throwable last:

```kotlin
PluginTemplate.logger.debug("Loading key '{}' from '{}'", key, sectionKey)
PluginTemplate.logger.error("Failed to load definition '{}'", definition.key, cause)
```

Messages are English, in code, even when player-facing text is Russian. Player-facing text
never goes through the logger — see [Messages and Internationalization](internationalization.md).

## Scheduling

Use `me.wyne.wutils.common.scheduler.Schedulers`, not `Bukkit.getScheduler()`:

```kotlin
regenTask = Schedulers.sync().runRepeating(Consumer { regenerateRaided() }, TICKS, TICKS)
Schedulers.async().execute { /* off-thread work */ }
```

Async database work returns `CompletableFuture` and hops back with
`thenAcceptAsync(..., Schedulers.sync())` before touching the world or opening a GUI.

## Teardown

Implement `Terminable` and bind the instance to the plugin from `init` — `bindWith(plugin)`
inside the class, or `plugin.bind(this)` — so `close()` runs on disable. `close()` stops
tasks, cancels tokens, despawns anything spawned, and shuts executors down. `DISABLE` steps
run *before* the terminable registry closes, so a disable step can still use bound resources.

Repeating tasks are also restarted by `load`, so `load` must stop the previous task first:
`expireTask?.stop()` then reschedule. Forgetting this doubles the task on every reload.
