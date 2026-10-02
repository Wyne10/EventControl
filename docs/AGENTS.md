# BigCraft Plugin Documentation

This is the shared style guide for the Bukkit/Paper plugin family built from
`KotlinPluginTemplate`: Kotlin plugins targeting Paper 1.16.5, wired with Guice, built on
the [WUtils](wutils.md) libraries. They all share one shape — the same step pipeline, the
same config and i18n conventions, the same module split.

Read [Plugin Anatomy](plugin-anatomy.md) first, then the convention page for whatever you
are about to touch. The infrastructure pages describe the server-wide plugins your plugin
depends on rather than reimplements.

This wiki covers *how this family writes plugins*. It does not document Bukkit, Kotlin,
Guice or the WUtils API surface — for the last of those, follow the links in
[WUtils Libraries](wutils.md).

- [Plugin Anatomy](plugin-anatomy.md)
- [Startup, Reload and Shutdown](conventions/lifecycle.md)
- [Dependency Injection](conventions/dependency-injection.md)
- [Configuration](conventions/configuration.md)
- [Loadables and Definitions](conventions/loadables.md)
- [Messages and Internationalization](conventions/internationalization.md)
- [Commands](conventions/commands.md)
- [Menus and Placeholders](conventions/menus-and-placeholders.md)
- [Persistence](conventions/persistence.md)
- [Items and Loot](conventions/items-and-loot.md)
- [Build and Packaging](conventions/build.md)
- [The Shared Version Catalog](conventions/version-catalog.md)
- [Server Infrastructure Plugins](infrastructure/infrastructure.md)
- [ConnectionSource](infrastructure/connection-source.md)
- [CustomItems](infrastructure/custom-items.md)
- [InfPoints](infrastructure/infpoints.md)
- [LootPool](infrastructure/loot-pool.md)
- [Storm](infrastructure/storm.md)
- [WUtils Libraries](wutils.md)
