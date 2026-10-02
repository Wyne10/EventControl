# ConnectionSource

One HikariCP pool for the whole server, exposed as an ORMLite `ConnectionSource`. It exists
so that ten plugins do not open ten pools against the same database.

Artifact: `org.bigcraft:ConnectionSource-api` (`libs.connectionSource`), `compileOnly`.
Plugins that store SQL data list `ConnectionSource` in `depend`.

## The API

- `ConnectionProvider` — what consumers use. `getConnectionPool()` returns a
  `ConnectionPool<ConnectionSource>`, `isActive` reports whether a pool exists and is
  running, `getConfig()` exposes the JDBC settings, `reloadConnectionPool()` re-opens it.
- `ConnectionPool<T>` — `getSource()` is the ORMLite `ConnectionSource` you hand to
  `DaoManager`; `getConnection()` gives a raw pooled JDBC `Connection` **that the caller must
  close**. `isActive()` reports whether the data source is running.
- `SqlConfig` — driver name, JDBC URL, username, password, `isConfigured()`.
- `CSApi.getProvider()` — static access, `null` until ConnectionSource enables.

The provider owns the pool. Never call `close()` on the pool you were handed, and never
build a `HikariDataSource` yourself.

## Getting it

Look it up once from the services manager, in its own Guice module, and inject
`ConnectionProvider` everywhere else:

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

## Using it

Guard on `isActive` before creating DAOs, rebuild them on every load, and let requests fail
loudly if the pool never came up. The full repository shape — executors, transactions, result
types, table creation — is in [Persistence](../conventions/persistence.md).

Because the pool is shared, table names must be plugin-specific and spelled out in
`@DatabaseTable(tableName = "…")` (`kit_usage`, not `usage`), and a plugin only ever creates
and migrates its own tables.
