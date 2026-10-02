# Storm

The unified way to give a player a timed, persisted effect — an XP booster, a potion buff, a
crop-growth bonus. Storm owns the per-player state, its expiry, its persistence across
sessions, and the history; plugins contribute effect *implementations* and apply them.

Artifact: `org.bigcraft:Storm-api` (`libs.storm`), `compileOnly`.

## Two layers

**Effect types** are classes registered under an `effectKey`
(`StormEffectRegistry.register(HunterEffect::class.java, "hunter")`). Storm ships `empty`,
`hunter`, `crops`, `commands` and `potion`.

**Effect instances** are configured entries under `effect:` in Storm's config (or in
`effect/*.yml`), each with its own key, a display `name`, the `effect` type it uses, and
whatever settings that type reads:

```yaml
effect:
  hunter:
    name: "Бустер опыта"
    effect: hunter
    multiplier: 1.25
```

Storm instantiates one object per configured instance, via a public constructor taking a
single `ConfigurationSection`. The instance key — not the effect key — is what you use at
runtime.

## Applying effects

`StormApi.getEffectManager()` (also registered as a Bukkit service):

- `setEffect(player, effectInstanceKey, durationMillis, source)` — applies or extends,
  replacing any remaining duration. Accepts an `OfflinePlayer`. Fires a cancellable
  `StormEffectApplyEvent`; throws `IllegalArgumentException` if the instance key is not
  configured. `source` is `EffectSource.PURCHASE` or `APPLY`, and is recorded in history.
- `clearEffect(player, effectInstanceKey)` — removes it and fires `StormEffectClearEvent`.
- `isAffected(player, key)` and `getRemainingMillis(player, key)` — online players only;
  both report "not affected" for offline or null players.

`StormEffectJoinEvent` fires per restored instance when a player with persisted effects
joins.

## Writing an effect type

Subclass `StormEffect`, take a `ConfigurationSection`, read your settings from it, and
register the class under a key. Every subclass is registered as a Bukkit `Listener`
automatically:

```kotlin
class HunterEffect(config: ConfigurationSection) : StormEffect(config) {
    private val multiplier = config.getDouble("multiplier", 1.0)

    @EventHandler(ignoreCancelled = true)
    private fun onEntityDeath(e: EntityDeathEvent) {
        val player = e.entity.killer ?: return
        if (!isAffected(player)) return
        e.droppedExp = ceil(e.droppedExp * multiplier).toInt()
    }
}
```

**Event handlers must call `isAffected(player)` themselves.** A listener is registered once
per configured instance and receives every matching event on the server, whoever it concerns.

For periodic work, also implement `TickableStormEffect`: `getPeriodTicks()` sets the period
and `tick(player)` runs on the main thread — Storm filters it to affected players for you, so
no `isAffected` call is needed there. All instances of one effect key share a single repeating
task, whose period comes from the first instance loaded.

Inside a `StormEffect`, `getConfig()`, `getEffectInstanceKey()`, `getPlugin()`,
`getLogger()`, `isAffected(player)` and `getRemainingMillis(player)` are available as
protected helpers.
