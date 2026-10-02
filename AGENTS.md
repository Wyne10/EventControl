# Agent Guide

**EventControl** — a Kotlin Paper 1.16.5 plugin that runs timed server events on queues,
and the published API third-party plugins register their own event types against. It
replaces `WorldEvents`, whose reflective "expansion" loader, centralised notification
plugin and per-plugin queue bookkeeping all failed.

The family style guide lives in **[docs/AGENTS.md](docs/AGENTS.md)** — read the page that
covers what you are about to change before changing it.

## The three ideas this plugin exists for

- **An event is resolved before it starts.** `EventType.prepare` runs `prepare-lead`
  before the start time and returns a `PreparedEvent` carrying every fact — location,
  variant, reward preview. Placeholders, `/event`, the poll ballot and the early
  announcement all read a `PreparedEvent`, never a running one. Anything only knowable at
  start time does not belong there.
- **The engine owns all scheduling state.** A slot is `PLANNED → PREPARING → PREPARED →
  RUNNING → FINISHED | CANCELLED`. Every consumer of the old plugin kept three maps —
  "requested", "delayed", "world" — which were these states. An event type owns none of it.
- **Event types are ordinary plugins.** They `depend: [EventControl]`, compile against
  `me.wyne:EventControl-api` and register into open registries from their own `onEnable`.
  No jar scanning, no `Unsafe`, no classloader surgery.

## Non-negotiables

- **`api/` may not reference a shaded type.** WUtils, Guice, helper and Adventure are
  relocated into the plugin's shadow package, and a relocated type in a published
  signature is unusable to a consumer. `./gradlew :api:checkApiPurity` enforces this; it
  runs as part of `check`.
- **Registries close one tick after the server finishes enabling**, in the `Load` step.
  That deferral must stay `Schedulers.sync().runLater(..., 1L)` — a sync scheduler called
  from the main thread runs its body *inline*, which closes the registries during this
  plugin's own `onEnable` and refuses every type a dependent registers.
- **Announcements anchored at or after the start only fire once the slot is `RUNNING`.**
  Otherwise a failed start still tells players the event began. The tick launches before
  it announces for the same reason.
- **A planned slot's start time is laid out from `nextAvailable`, not from `now`.** The
  anchor moves only when the queue starts and when an event ends. Recomputing against a
  moving `now` pushes the head away forever; only pushing slots later (never earlier)
  leaves the timeline permanently drifted, because a slot is planned before its source has
  chosen a definition and its length is a guess until then.
- **Lifecycle**: no logic in `onEnable`. Add a `@Step` object to `PluginSteps.kt` and list
  it in `addSteps(...)`. Reload never rebuilds the Guice injector; services reconcile
  their own live state in `load`, which must be idempotent.
- **Keys match `[a-z0-9-]+`.** PlaceholderAPI splits its parameters on `_`, so a key
  containing one cannot be addressed in a placeholder. Rejecting it at load beats shipping
  example keys shaped to dodge the problem.
- **No `on:` key in config.** YAML 1.1 resolves a bare `on` to the boolean `true` even as
  a mapping key, so an `on: end` entry silently parses as something else. Announcements
  use `at: end` / `at: end-10s` instead.
- **Text**: no user-facing string literals in Kotlin. Keys go in `lang/ru.yml`, sent with
  `player.placeholderComponent("key", "x" replace value)`. Admin commands answer the
  console too, so use `sendMessage(sender)`, not `sendMessagePlayer`.

## Layout

| Path | Contents |
|---|---|
| `api/` | Plain Java against Bukkit — the SPI and consumer types. Published as `me.wyne:EventControl-api`. |
| `src/main/kotlin/me/wyne/eventcontrol/queue/` | `EventQueue`, the engine and its tick. |
| `.../event/` | Definitions, the placeholder sink, the announcer, the built-in `demo` type. |
| `.../source/` + `.../poll/` | What decides the next event. A poll is a source like any other. |
| `.../registry/` | The registries and the close-after-one-tick gate. |

`plugins/EventControl/queue/<key>.yml` is a timeline; `plugins/EventControl/event/<key>.yml`
is a configured event. Both namespaces are separate, unlike the old `events.<queue>.<event>`
tree where an event could not be called `name`.

## Build

`libs/` is a git submodule holding the shared version catalog, symlinked in as
`gradle/libs.versions.toml`. Clone with `--recurse-submodules` and reference dependencies
as `libs.<alias>` — never a literal coordinate. `./gradlew runServer` starts a test server;
`./gradlew :api:publishToMavenLocal` installs the API for a consumer to build against.

`EventMeadow` (sibling project) is the reference event type and the acceptance test for the
API: anything it cannot express is an API gap, not a plugin problem.
