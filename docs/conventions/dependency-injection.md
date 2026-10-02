# Dependency Injection

Every plugin wires its singletons with Guice, created once by the `InitializeInjector` step
and never rebuilt. Modules live in `module/` and are split into three files by *why* a
binding exists.

## PluginModule

`module/PluginModule.kt` binds the platform objects, and is the only module that takes
constructor arguments:

```kotlin
class PluginModule(private val plugin: PluginTemplate) : AbstractModule() {
    override fun configure() {
        bind(PluginTemplate::class.java).toInstance(plugin)
        bind(Plugin::class.java).toInstance(plugin)
        bind(FileConfiguration::class.java).toInstance(plugin.getConfig())
    }
}
```

Because of this, a service takes the plugin type in its constructor rather than reaching
for `<Plugin>.instance`. Prefer the injected reference in constructors; `instance` is for
static-context helpers (`LoadableProvider`, factories, `object` steps).

## CoreModules

`module/CoreModules.kt` holds a private `CoreModule(vararg Class<*>)` implementation and
then one `val` per feature group:

```kotlin
val StructureModule: AbstractModule = CoreModule(StructureProvider::class.java, StructureService::class.java)
val ContainerModule: AbstractModule = CoreModule(UniqueContainerService::class.java)
```

Core modules bind unconditionally. Group by feature, not by type — one module per domain
area, listing that area's singletons.

## OptionalModules

`module/OptionalModules.kt` holds two private implementations:

- `OptionalModule(className, exceptionMessage, vararg modules)` — probes for a class with
  `Class.forName` and binds only if the soft dependency is installed, logging a warning
  otherwise. This is how PlaceholderAPI, CommandAPI, AbstractMenus, InvUI, ProtocolLib and
  DecentHolograms integrations stay optional.
- `ConfigurableModule(configurationPath, vararg modules)` — binds only if a config boolean
  is true.

```kotlin
val CommandModule: AbstractModule = OptionalModule(
    className = "dev.jorel.commandapi.CommandAPI",
    exceptionMessage = "CommandAPI not found, commands are not registered",
    PluginTemplateCommand::class.java
)
```

The template ships both implementations even where unused — keep them, and add the `val`s
you need.

## Service-lookup modules

An infrastructure plugin that publishes through Bukkit's `ServicesManager` gets its own
module file, so the lookup happens once:

```kotlin
object ConnectionModule : AbstractModule() {
    override fun configure() {
        try {
            Class.forName("org.bigcraft.connection.api.ConnectionProvider")
            bind(ConnectionProvider::class.java)
                .toInstance(Bukkit.getServicesManager().getRegistration(ConnectionProvider::class.java)!!.provider)
        } catch (_: ClassNotFoundException) {
            PluginTemplate.logger.error("ConnectionSource not found, can't establish database connection")
        }
    }
}
```

The richer variant binds an interface of your own: resolve the optional service, wrap it in
an adapter, and fall back to a no-op implementation (`SomeThing.EMPTY`) when it is absent, so
the rest of the plugin calls the interface and never checks for presence.

## Conventions for injected classes

- Annotate with `@Singleton` and use `@Inject constructor(...)`.
- Do the side effects (registering listeners, registering with `Config.global` and
  `Loader.global`, setting a `companion object` `instance`) in `init`. Construction *is*
  registration; nothing calls a separate `setup()`.
- A class not listed in a module is never constructed. Adding a service means adding it to a
  module.
