---
description: >-
  The five keys in config.yml, the language files that name your events, and
  everything the plugin writes under plugins/EventControl.
---

# Configuration

The plugin's own settings are small — the interesting parts live in the queue and event files. `plugins/EventControl/config.yml` is generated on first startup:

```yaml
regenerate: true
lang: 'en.yml'
usePlayerLanguage: true
# LEGACY/ENHANCED_LEGACY/MINI_MESSAGE
serializer: MINI_MESSAGE
# OFF/FATAL/ERROR/WARN/INFO/DEBUG/TRACE/ALL
logLevel: INFO
```

| Key                 | What it does                                                                                                               |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `regenerate`        | Whether to merge new keys into `config.yml` on each startup. See [Regeneration](configuration.md#regeneration).            |
| `lang`              | The language file used for the console and for players whose own language is not known.                                    |
| `usePlayerLanguage` | Whether to render each player's messages in their own client language, falling back to `lang`.                             |
| `serializer`        | How markup in messages is parsed. See [Serializers](configuration.md#serializers).                                         |
| `logLevel`          | Threshold for the plugin's own logging. `DEBUG` logs every definition as it loads and every slot transition as it happens. |

{% hint style="info" %}
`logLevel: DEBUG` is the single most useful setting while setting a queue up. It is what tells you whether a slot was planned, what its source answered, when preparation started and when it finished — all the things a timeline does silently when it is working.
{% endhint %}

## Serializers

`serializer` picks the markup dialect for every string the plugin renders:

| Value             | Markup                                                                                   |
| ----------------- | ---------------------------------------------------------------------------------------- |
| `LEGACY`          | `&`-codes                                                                                |
| `ENHANCED_LEGACY` | `&`-codes plus [EnhancedLegacyText](https://github.com/Vankka/EnhancedLegacyText) markup |
| `MINI_MESSAGE`    | [MiniMessage](https://docs.advntr.dev/minimessage/format.html)                           |

The choice is server-wide, and it applies to the language files as they ship — which are written in MiniMessage. Switching to `LEGACY` without rewriting them shows their tags verbatim.

It also decides what an event's display name looks like to a plugin that reads it through a placeholder, which matters when that plugin parses a different dialect. See the display variants.

## Languages

`plugins/EventControl/lang/` holds one file per language — `en.yml` and `ru.yml` ship with the plugin. Each is a flat map of keys to markup, grouped by role:

```yaml
### Format
format-slot-entry: " <gray><index>. <white><event> <gray><state>/<origin> …"

### Messages
info-event-upcoming: "<gray>▶ <white>Next: <gold>%eventcontrol_<event>% …"
error-unknown-queue: "<red>▶ <white>No such queue: <red><queue>"
success-queue-started: "<green>▶ <white>Queue <green><queue> <white>started"

### Events
event-demo: "<aqua>Demo event"
```

Two kinds of substitution appear in them. `<queue>`, `<event>`, `<starts-in>` and friends are filled in by the plugin, and each message takes its own set. `%eventcontrol_…%` and `%dtf_…%` are PlaceholderAPI placeholders, resolved afterwards and only if PlaceholderAPI is installed — which is how a raw millisecond count in a message becomes a readable countdown.

Add a language by dropping a file next to the shipped ones; the file name is what `lang` and the player's client language are matched against.

### The `### Events` block

The last block is different in kind, and it is the one to know about:

```yaml
event-demo: "<aqua>Demo event"
event-demo-wide: "<aqua>Demo event (wide search)"
```

These are the display names of your events, named by each event file's `display` key. **They live here even for events provided by another plugin.** The event file belongs to EventControl, so the key written in it points into EventControl's language files — a plugin that ships an event kind documents the key it expects, and you write the name. An unresolved key renders as its own name, so a player reading `event-shrine` in chat means the key is missing from this file.

## Regeneration

With `regenerate: true` — the default — each startup merges the version's own defaults into `config.yml`: keys a new version added appear, with their comments, and your values are kept. The defaults it merges from are written to `defaults/`, and a copy of your file as it was goes to `backups/` first. Set it to `false` and `config.yml` is left exactly as you wrote it, at the cost of adding new keys by hand after an update.

Regeneration only touches `config.yml`. The `queue/` and `event/` directories are a different story: the plugin unpacks the files it ships **only when the directory does not exist yet**, on the very first startup. After that the directory is yours. Nothing you write there is overwritten, and nothing you delete comes back.

## Files the plugin writes

| Path                               | Contents                                                                                                      |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `plugins/EventControl/config.yml`  | The settings above.                                                                                           |
| `plugins/EventControl/queue/`      | One file per queue. See [Writing a queue](writing-a-queue.md).                                                |
| `plugins/EventControl/event/`      | One file per event. See [Writing an event](writing-an-event.md).                                              |
| `plugins/EventControl/lang/`       | Language files, including your events' display names.                                                         |
| `plugins/EventControl/queues.json` | Each queue's pending timeline, so a restart resumes it. Written as it changes and on shutdown. Don't edit it. |
| `plugins/EventControl/defaults/`   | The generated defaults regeneration merges from. Don't edit it.                                               |
| `plugins/EventControl/backups/`    | Copies of `config.yml` from before each regeneration.                                                         |

## Applying changes

Edit anything under `plugins/EventControl/`, then run `/eventcontrol reload` or restart the server. A reload re-reads the config, the language files and every queue and event file, and rebuilds the live timelines from them.

What survives a reload is worth being precise about:

| Survives                                                       | Rebuilt                                                                |
| -------------------------------------------------------------- | ---------------------------------------------------------------------- |
| Event kinds and queue sources registered by plugins.           | Every event definition, read fresh from `event/`.                      |
| The persisted timeline, which is written out before reloading. | Every queue, read fresh from `queue/` — one deleted from disk is gone. |

{% hint style="warning" %}
A reload ends a running event rather than orphaning it: the queue is rebuilt, so the old live instance is torn down through the normal end path first. Anything a prepared slot had already allocated is released the same way. Players see an event stop, so prefer reloading between events rather than during one.
{% endhint %}
