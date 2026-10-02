# Server Infrastructure Plugins

Five plugins on the server exist so that feature plugins do not each solve the same problem.
Each publishes a small `-api` artifact you depend on `compileOnly`, and each is reached
through a static holder and/or Bukkit's `ServicesManager` — never by casting the plugin
instance.

| Plugin | Owns | API entry point | Artifact |
|---|---|---|---|
| [InfPoints](infpoints.md) | currencies / point balances | `IPApi.getInstance()` | `org.bigcraft:InfPoints-api` |
| [ConnectionSource](connection-source.md) | the shared HikariCP + ORMLite SQL pool | `ConnectionProvider` service | `org.bigcraft:ConnectionSource-api` |
| [LootPool](loot-pool.md) | predefined items and drop tables | `LootPoolApi.getProvider()` | `org.bigcraft:LootPool-api` |
| [CustomItems](custom-items.md) | items with behaviour, custom blocks | `CustomItemsApi.getProvider()` | `org.bigcraft:CustomItems-api` |
| [Storm](storm.md) | persisted, timed player effects | `StormApi.getEffectManager()` | `org.bigcraft:Storm-api` |

## Rules that apply to all five

- **Depend, don't reimplement.** No plugin opens its own connection pool, invents its own
  currency, or defines its own "give the player this exact item" config format.
- The API artifact is `compileOnly`, referenced from the shared version catalog
  (`libs.infPoints`, `libs.connectionSource`, `libs.lootPool`, `libs.customItems`,
  `libs.storm`) — see [Build and Packaging](../conventions/build.md).
- Declare the plugin in `depend` if the feature cannot work without it, in `softDepend` if it
  degrades. A hard `depend` is what makes the static holders safe to call after enable.
- Bind the API into Guice once, in a module, rather than calling the static holder from
  everywhere. For a service-manager API that means a dedicated module file
  (`ConnectionModule`); for a probe-and-degrade API, an `OptionalModule` or a small wrapper
  object holding a single `Class.forName` result.
- Every static holder returns `null` until its plugin has enabled. Never touch one from a
  static initializer or from `onLoad`.
