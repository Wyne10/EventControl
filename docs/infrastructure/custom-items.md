# CustomItems

Items that *do* something: activators, cooldowns, restrictions, AoE effects, traps, and
custom blocks. Use it when the behaviour is the point. When you only need a specific
`ItemStack`, use [LootPool](loot-pool.md) instead — that rule is stated in full in
[Items and Loot](../conventions/items-and-loot.md).

Artifact: `org.bigcraft:CustomItems-api` (`libs.customItems`), `compileOnly`.

## Model

- `CustomItemApi` — one item definition. `createItemStack(player)` builds a tagged stack
  (the `player` argument resolves placeholder-dependent attributes and may be `null`);
  `applyItemStack(item, player)` re-applies attributes onto an existing stack;
  `isCustomItem(stack)` / `isCustomItem(block)` recognise it; `getItem(inventory)` and
  `getItems(inventory)` find it. The `PlayerInventory` overload checks hand, off-hand and
  armour first.
- `CustomItemProvider` — the registry: `getKeys()`, `getPaths()`, `contains(key)`,
  `getItem(key)`, and `getItem(stack)` which resolves a stack back to its definition through
  the persistent-data tag it was created with.
- `CustomBlockRegistry` — which world blocks were placed as custom items:
  `isCustomBlock(block)`, `getBlock(block)`, `placeBlock(block, item)`, `breakBlock(block)`.
  It tracks locations only; it never changes the world.
- `CustomItemsApi.getProvider()` / `getBlockRegistry()` — static access, `null` before
  CustomItems enables.

Because recognition is tag-based, `isCustomItem` keeps working after a rename or a lore
change — which is exactly why item identity checks should never compare display names.

## Items are configured, not coded

Each item is a YAML file in CustomItems' `item/` directory, and the vocabulary
(`material`, `name`, `restrictions`, `activators` with conditions and actions) belongs to
CustomItems. Adding an item is a config change, not a code change:

```yaml
name: '<gold>АнтиФантом'
material: PHANTOM_MEMBRANE
restrictions:
  cancel-storage: [BREWING, ANVIL]
activators:
  entity-hit-player:
    is-damager-type: [PHANTOM]
    set-damager-health: 0.0
    cancel-event: true
```

Only write code against the API when your plugin needs to *react* to a custom item
(recognise it in a handler, hand one out, check a placed custom block).

## Bridging to LootPool

`CustomKeyedLoot` is a one-entry `LootPool` whose item is resolved from the CustomItems
registry on every access, so the pool reflects later edits to the item definition. Use it to
put a behavioural item into a drop table without freezing a copy of the stack.
