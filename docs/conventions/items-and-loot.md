# Items and Loot

Any item a plugin needs to hand out, drop, or recognise is **defined outside the plugin**.
The plugin stores a key, resolves it at use time, and compares stacks with `isSimilar`.

## Pick the right source

1. **[LootPool](../infrastructure/loot-pool.md) — the default.** A single predefined item is
   a `KeyedLoot` (a one-entry pool); a drop table is a `BasicLootPool`, and pools compose
   (`MultiLootPool`, `CompositeLootPool`, `RollLootPool`). Server owners create and edit them
   in-game with `/lootpool`, so no code or restart is needed to change an item.
2. **[CustomItems](../infrastructure/custom-items.md) — only when its own API is required.**
   Reach for it when the item needs behaviour CustomItems provides: activators, cooldowns,
   restrictions, custom blocks, AoE, traps, or recognising an item that a player has been
   carrying since before your feature existed. If all you need is "this exact ItemStack",
   LootPool is the right answer and CustomItems is the wrong one.
3. **`ItemConfigurable`** (`wutils-configurables`) for an item that belongs to *your*
   plugin's config and has no meaning outside it (a GUI button, a decorative filler).
4. Constructing an `ItemStack` in Kotlin is for placeholders and filler panes only.

A CustomItems item can still be exposed as a pool entry with `CustomKeyedLoot`, which
resolves the custom item on every access; use that instead of copying the stack into a
`KeyedLoot` if the item's definition may change.

## Referencing a pool

Store the key as an `@ConfigEntry` string, resolve through the provider, expose the result
as a computed property so reloads pick up edits:

```kotlin
@ConfigEntry(section = "kit", comment = "Keyed loot please")
private val unlockKey = "kit-unlock-key"

private val unlockItem: ItemStack?
    get() = (LootPoolApi.getProvider().getLootPool(unlockKey) as? KeyedLoot)?.loot?.item
```

Resolve with `as? KeyedLoot` (soft) or `requireNotNull(...) { "…" }` (hard) depending on
whether the feature can run without it. `LootPoolApi.getProvider()` is only valid after
LootPool has enabled — that is what the `depend`/`softdepend` entry in
`build.gradle.kts` guarantees.

## Comparing items

To decide whether a stack in a player's hand *is* the configured item, use Bukkit's
`isSimilar` — it compares type and metadata while ignoring amount:

```kotlin
val unlock = unlockItem
if (unlock != null && !unlock.isSimilar(item)) {
    event.isCancelled = true
    event.player.placeholderComponent("info-loot-locked")
        .replace("item" replaceComponent unlock.itemMeta.displayNameComponent)
        .sendMessage(event.player)
    return
}
```

- Compare **template `isSimilar` candidate**, and null-check the stack first
  (`isNullOrAir()` / `isNotNullOrAir()` from `wutils-common-kotlin`).
- Never compare display names or lore as strings to identify an item; that breaks the moment
  a translation or a colour changes. Offer name- or type-only matching solely where an admin
  explicitly picks that mode as a search option.
- For a CustomItems item, use `CustomItemApi.isCustomItem(stack)` or
  `CustomItemProvider.getItem(stack)` instead — it reads the persistent-data tag and
  survives renames.
- Consume with `item.amount--` on the stack the event handed you; do not rebuild it.

## Giving loot out

`LootPool` implements Bukkit's `LootTable`, so a pool can be used wherever the platform
expects one. Otherwise use the pool's own methods: `populate(slots)` for a list,
`populate(inventory, slots)` to fill in slot order, `populateRandomly(inventory, slots)` to
scatter, and `getRandom()` for a single weighted entry. `Loot.create()` rolls the amount;
`Loot.create(amount)` pins it. Rolled stacks are clones — the template is never mutated.
