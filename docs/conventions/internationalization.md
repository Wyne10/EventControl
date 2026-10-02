# Messages and Internationalization

No user-facing string is written in Kotlin. Every message lives in `lang/ru.yml`, is fetched
by key, and is sent as an Adventure component through `wutils-i18n`.

## Setup

`InitializeI18n` builds `I18n.global` once, from the config header
([Configuration](configuration.md)): `serializer` picks the markup interpreter
(`MINI_MESSAGE` for new plugins), `usePlayerLanguage` enables per-player language, and
`loadLanguage("lang/ru.yml")` copies the bundled default out on first run. Extra language
files dropped into `<dataFolder>/lang/` are picked up automatically.

Russian (`ru.yml`) is the shipped default across the family. The template no longer ships an
`en.yml`; do not add one unless the plugin is actually translated.

## Key naming

Language files are grouped by comment banners and keys are prefixed by role:

```yaml
### Format
format-locate-entry: " <white>▶ …"

### Messages

## Info
info-unique-key-acquired: "<#fe9600>▶ <white>%player_name% нашел <item><reset>!"

## Error
error-structure-key: "<red>▶ <white>Неизвестный ключ структуры"

## Success
success-plugin-reload: "<green>▶ <white>Плагин перезагружен!"
```

- `info-…`, `error-…`, `success-…` for messages; `format-…` for fragments composed into
  other messages; GUI keys carry their own suffixes (`…-name`, `…-title`, `…-button-name`).
- Placeholders are `<angle-bracket>` names filled by replacements; `%percent%` names are
  PlaceholderAPI and are resolved by the `placeholder*` accessors only.

## Sending a message

Use the Kotlin extensions from `me.wyne.wutils.i18n.kotlin` on the *recipient*
(`Player`, `OfflinePlayer`, `CommandSender`, all nullable-friendly):

```kotlin
sender.placeholderComponent("success-plugin-reload").sendMessage(sender)

player.placeholderComponent("error-title-length", "length" replace title.length)
    .sendMessage(player)

player.placeholderComponent("info-unique-key-acquired")
    .replace("item" replaceComponent itemName)
    .sendMessagePlayers()
```

- `placeholderComponent(path, vararg replacements)` — resolves in the recipient's language
  and runs PlaceholderAPI. This is the default choice.
- `localizedComponent(...)` — same without PlaceholderAPI; `placeholderString(...)` /
  `placeholderStrings(...)` when you need raw text (command dispatch, GUI titles, lore).
- `key replace value` builds a `TextReplacement` (substituted **before** markup parsing —
  the value cannot inject markup); `key replaceComponent value` builds a
  `ComponentReplacement` for splicing a real `Component` (an item name, a formatted title).
- Send with `sendMessage(target)`, `sendMessagePlayer(sender)` (no-op for console),
  `sendMessagePlayers()`, `sendMessageConsole()`, `sendActionBar(player)`.
- `.bungee` / `.bungee()` converts to the `BaseComponent[]` that CommandAPI and InvUI item
  builders want; `.legacy`, `.plain` exist for the same reason.

The receiver decides the language, so pass the player who will *read* the message. In a GUI
where an admin inspects another player, that is the viewer, not the subject — keep the two
references separately named (`viewer` and `player`) so the wrong one is hard to pass.

## Adding a message

1. Add the key to `src/main/resources/lang/ru.yml` under the right banner.
2. Reference it by key at the call site — never a literal fallback string in code.
3. If the message takes values, pass them as replacements; do not build the sentence with
   string concatenation.
