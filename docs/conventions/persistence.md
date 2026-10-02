# Persistence

Three storage options are in use, in this order of preference.

## SQL through ConnectionSource

Relational, cross-plugin, or large data goes to SQL — but a plugin never builds its own
connection pool. It takes a `ConnectionProvider` from
[ConnectionSource](../infrastructure/connection-source.md) and creates ORMLite DAOs on it:

```kotlin
@Singleton
class KitRepository @Inject constructor(
    plugin: PluginTemplate,
    private val connectionProvider: ConnectionProvider
) : Loadable, Terminable {

    private var usageDao: Dao<KitUsage, Long>? = null

    override fun load(config: ConfigurationSection) {
        close()
        if (connectionProvider.isActive) {
            usageDao = DaoManager.createDao(connectionProvider.connectionPool.source, KitUsage::class.java)
            if (!usageDao!!.isTableExists)
                TableUtils.createTable(connectionProvider.connectionPool.source, KitUsage::class.java)
            return
        }
        PluginTemplate.logger.error("Couldn't connect to the database, subsequent requests will fail")
    }
}
```

The shape to copy:

- DAOs are nullable fields rebuilt in `load`, so a reload picks up a re-pointed pool. Call
  sites use `requireNotNull(usageDao)` — a missing pool fails loudly, it does not silently
  no-op.
- Tables are created by the owning plugin with `TableUtils.createTable` when absent.
- Every public method returns `CompletableFuture<…>` off a plugin-owned `Executor`
  (`Executors.newSingleThreadExecutor()` for ordered writes, a cached pool for reads),
  recreated in `load` and shut down in `close()`.
- Multi-statement work runs inside `TransactionManager.callInTransaction(source) { … }`.
- Expected failures are values, not exceptions: return a sealed
  `Result.Success/Failure(Error)` pair, where the error enum carries the i18n key of the
  message to show.
- Entities are `data class`es annotated with `@DatabaseTable`/`@DatabaseField`, with column
  name constants in the companion object. Default values on every field, so ORMLite can
  construct them.

Never touch the world or send messages from the executor thread — hop back with
`thenAcceptAsync(..., Schedulers.sync())`.

## Caching for read paths

Placeholders, catalogs and menus read from an in-memory snapshot cache in a `cache/`
package, not from the DAOs. The cache is refreshed by the owning service on load and after
each mutation, and holds immutable snapshot records rather than live ORMLite entities.

## JSON

Small, plugin-local, non-relational state uses `wutils-json`: the `InitializeJson` step
installs `JsonRegistry.global`, `Load` calls `load()`, and the `Write` step (referenced from
both `Disable` and `Reload`) calls `write()`. If a plugin has no JSON state, drop the
`InitializeJson` step and the `Write` step together.

## Files on disk

Definition YAML under `<dataFolder>/<section>/` is loaded, not written — see
[Loadables and Definitions](loadables.md). The exception is a service that persists live
world state: it owns a `<dataFolder>/data` directory, writes one memento file per live
object as it spawns and despawns, and does the IO on its own single-threaded executor.
