# Configuration

Config access follows one rule: **a value a server owner can change is a field, not a
lookup.** Raw `config.getString(...)` calls appear only in the bootstrap steps, before
anything else is initialized.

## The shared config.yml header

Every plugin's bundled `src/main/resources/config.yml` starts with the same block, and the
bootstrap steps read exactly these keys directly:

```yaml
regenerate: true
lang: 'ru.yml'
usePlayerLanguage: true
# LEGACY/ENHANCED_LEGACY/MINI_MESSAGE
serializer: MINI_MESSAGE
# OFF/FATAL/ERROR/WARN/INFO/DEBUG/TRACE/ALL
logLevel: INFO
```

Everything below that block is generated from `@ConfigEntry` fields — do not hand-write it
into the bundled file.

## @ConfigEntry fields

Declare the setting where it is used, give it a real default, and register the object:

```kotlin
@Singleton
class KitService @Inject constructor(...) : Loadable {

    @ConfigEntry(section = "kit")
    val purchasePrice = 5_000_000.0

    @ConfigEntry(section = "kit")
    val maxNameLength = 12

    @ConfigEntry(section = "kit", comment = "Keyed loot please")
    private val rewardKey = "kit-reward"

    init {
        Config.global.registerConfigObject(this)
    }
}
```

Notes that bite:

- `registerConfigObject` reads **declared fields only** — a field on a superclass is never
  picked up. Register the concrete instance.
- `section` is required; `path` defaults to the field name. Section naming is not
  consistent across the family — some plugins use dotted `Feature.Group` sections, others a
  single lowercase word. Match the plugin you are in rather than introducing a third style.
- Fields are read at load time, so read them as plain properties at use sites. Values that
  depend on a field (a currency `Point`, an `ItemStack` from a loot key) are exposed as
  computed `get()` properties so they resolve after reload:

```kotlin
private val rewardItem: ItemStack?
    get() = (LootPoolApi.getProvider().getLootPool(rewardKey) as? KeyedLoot)?.loot?.item
```

## Typed values: use configurables

Do not parse strings by hand. `wutils-configurables` covers durations, ranges, items,
sounds, animations, GUIs and maps:

```kotlin
@ConfigEntry(section = "kit")
private val cooldown = TimeSpanConfigurable("100m")   // .timeSpan.millis

@ConfigEntry(section = "kit")
val upgradePrice = GenericMapConfigurable(
    mapOf<Int, Args>(1 to Args("1:100000")),
    { entry -> MapUtils.entry(entry.key.toString(), "1:100000") },
    { entry -> MapUtils.entry(entry.key.toInt(), Args(entry.value as String)) }
)
```

See the WUtils [Configurables](../wutils.md) documentation for the full catalogue before
writing a new one.

## Reading sections directly

Definition files (see [Loadables and Definitions](loadables.md)) are read through
`ConfigurationSection`, and there the Kotlin helpers from `wutils-common-kotlin` apply:

```kotlin
val section = config.deep(key)                                  // section-or-self
section.getBlockMaterial("material", Material.PURPLE_SHULKER_BOX)
section.getEnumSet<Material>("blocks")
requireNotNull(section.getLocation("location")) { "Unique container location is null" }
```

Missing-but-required values fail loudly with `requireNotNull(...) { "message" }`; the
loader catches the throw, logs it against the key, and keeps loading the rest.

## Never do this

- `Bukkit.getPluginManager().getPlugin("X").config` — read your own config only.
- Caching a config value in a `val` initialized at construction time when the class is not
  registered with `Config.global`; it will not survive a reload.
- Adding a key to the bundled `config.yml` for a value that already has an `@ConfigEntry`
  field. The generator owns that part of the file.
