# Menus and Placeholders

There is no single GUI library in the family. Which one to use is decided by *who owns the
layout*.

## Config-owned menus: AbstractMenus

If a server owner should be able to lay the menu out, the plugin ships **no GUI code**. It
registers catalogs and properties with [AbstractMenus](https://abstractmenus.github.io) and
the menu itself is a HOCON file the owner edits. This is the default for player-facing
menus.

A catalog turns a plugin collection into menu rows; an extractor maps a placeholder name on
a row to a string:

```kotlin
class KitCatalog : Catalog<KitSnapshot> {
    override fun snapshot(player: Player, menu: Menu): Collection<KitSnapshot> =
        KitService.instance.cache.all().sortedByDescending { it.uses }
    override fun extractor() = KitExtractor()

    class Serializer : NodeSerializer<KitCatalog> { … }
}
```

All catalogs are registered from one `@Singleton` class in `menu/`:

```kotlin
@Singleton
class AbstractMenusRegistry {
    init {
        Types.registerCatalog("KITS", KitCatalog::class.java, KitCatalog.Serializer())
        Types.registerCatalog("OWNERS", OwnerCatalog::class.java, OwnerCatalog.Serializer())
    }
}
```

bound through an `OptionalModule` on `ru.abstractmenus.api.AbstractMenusPlugin`. Commands
that "open a menu" then just dispatch the configured console command:

```kotlin
@ConfigEntry(section = "Command")
private val execute = "am open kits %player_name%"
…
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), sender.placeholderString(execute).get())
```

Extractors reuse the plugin's own PlaceholderAPI expansion rather than duplicating format
logic — the extractor delegates to the expansion's `onRequest`.

## Code-owned menus: InvUI

If the layout is part of the feature (an admin inspection screen, a diff view), build it in
code with [InvUI](https://github.com/NichtStudioCode/InvUI) in a `gui/` package: a structure
string, ingredients per marker char, `AbstractItem` subclasses for buttons, and
`Window.single()`.

**Do not shade InvUI.** The server provides a standalone `InvUi` plugin carrying InvUI 1.49,
so depend on `libs.invUi` / `libs.invUiKotlin` as `compileOnly` and list `InvUi` in
`softDepend` — the same treatment as any other server-provided library. Guard the GUI code
behind an `OptionalModule` probing an InvUI class if the plugin should still start without
it.

Rules that carry over from the rest of the codebase:

- Item names and lore come from i18n keys, converted with `.bungee()`; only genuinely
  decorative filler (`GRAY_STAINED_GLASS_PANE`) is built inline.
- Titles too: `.setTitle(player.placeholderComponent("…-title", …).bungee())`.
- Anything slow (skull textures, database reads) happens on `Schedulers.async()` and calls
  `notifyWindows()` from `Schedulers.sync()` when it lands.
- The click handler resolves the target from the click type rather than opening a
  confirmation dialog.

`wutils-configurables` also offers `GuiConfigurable` (triumph-gui) and
`InvUiItemConfigurable` when you want a code-driven menu whose *buttons* are configurable.
Older plugins in the family use triumph-gui; prefer InvUI for new code-owned screens.

## PlaceholderAPI expansions

Placeholders live in `placeholder/`, one `PlaceholderExpansion` per subject — a collection
expansion, a per-entity expansion, a per-member expansion — each `@Singleton`, registered in
`init`, and bound through the PlaceholderAPI `OptionalModule`.

- `getIdentifier()` is the short subject name (`kit`), `persist()` returns `true`, and
  `getVersion()` reads `plugin.description.version`.
- Parse `params` with `Args(params, "_")` and return `null` for anything unrecognized.
- Read from the in-memory cache/snapshot, never from the database — placeholders are called
  on the main thread, per frame, per player.
- Expose a `companion object instance` when catalogs or other classes need to reuse the
  formatting.
