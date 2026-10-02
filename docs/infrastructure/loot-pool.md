# LootPool

The server's registry of predefined items and drop tables. It is the default answer to
"my plugin needs to give the player a specific item" — see
[Items and Loot](../conventions/items-and-loot.md) for when it is *not*.

Artifact: `org.bigcraft:LootPool-api` (`libs.lootPool`), `compileOnly`, plugin listed in
`depend`.

## Model

- `Loot` — one entry: an `ItemStack` template, a relative `weight`, and an inclusive
  `minAmount..maxAmount` range. `create()` clones the template and rolls the amount;
  `create(amount)` pins it. The template is never mutated.
- `LootPool` — a weighted collection of entries. It extends Bukkit's `LootTable`, so a pool
  can be used anywhere the platform wants one. Key methods: `getRandom()`, `populate(slots)`,
  `populate(inventory, slots)`, `populateRandomly(inventory, slots)`.
- `LootPoolProvider` — the registry: `getLootPool(key)`, `getLootPoolMap()`,
  `getMapOf(Class)`, plus `addLootPool`/`writeLootPool`/`removeLootPool` for editors.
  Reached via `LootPoolApi.getProvider()` or the services manager.

## Pool types

| Type | Use it for |
|---|---|
| `KeyedLoot` | exactly one predefined item — the "named item" case |
| `BasicLootPool` | a self-contained weighted drop table (the editable one) |
| `MultiLootPool` | merge several pools' entries into one flat table |
| `CompositeLootPool` | weight whole pools against each other (80% common, 20% rare) |
| `RollLootPool` | wrap a pool and vary how many items a use draws |
| `SnapshotLootPool` | a fixed layout pinned to inventory slot indices |
| `VanillaLootPool` | expose a vanilla `NamespacedKey` loot table through the API |

Referencing types (`Multi`, `Composite`, `Roll`) resolve their targets through the provider
on **every** call, so edits to a referenced pool take effect immediately, and keys that no
longer resolve are skipped silently rather than erroring.

## Consuming a pool

Store keys in config, resolve at use time:

```kotlin
val lootSection = section.getConfigurationSection("loot")
val loot: Map<LazyMask, LootPool> = lootSection
    ?.getKeys(false)
    ?.associate { key -> LazyMask(key) to LootPoolApi.getProvider().getLootPool(lootSection.getString(key)!!)!! }
    ?: emptyMap()
```

For a single item, cast to `KeyedLoot` and take `loot.item` as the template to compare with
`isSimilar`. Pools are read-only from a consumer's point of view — `addLootPool` and
`writeLootPool` belong to LootPool's own commands and editors.

## Authoring pools

Pools are created and edited in-game with `/lootpool`, which is why plugins should not ship
their own item YAML: `pool` subcommands manage basic pools (create, modify, clone, merge,
weight, amount, flatten), `item` subcommands manage keyed items from the stack in your hand,
and root subcommands cover compose, include, roll, snapshot, give, drop, fill, populate and
preview. Pools persist to LootPool's own data folder.
