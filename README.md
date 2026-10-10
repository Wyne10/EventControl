---
description: >-
  Server events on a timeline settled before it happens, so what runs next,
  where and when are readable in advance. New event kinds, and new ways of
  choosing them, come from other plugins.
---

# EventControl

EventControl is a Bukkit/Paper plugin that runs server events on a schedule: a queue is a timeline of what happens when, written as a YAML file, and the events it runs come from other plugins through its API.

Most event plugins own both halves — the schedule and the events — so a new kind of event means a fork or a feature request. EventControl owns only the schedule. _What kinds of event exist_ and _what decides which one runs next_ are registries any plugin can add to, and the event that ships in the jar is a worked example rather than content.

The reason it is worth separating at all is narrower than either half: **an event is fully resolved before it starts.** A queue picks its next event one `prepare-lead` early and resolves everything about it — the location, the chosen variant, the loot preview — while nothing is visible in the world yet. That is what makes a schedule showable: a placeholder, the `/event` screen and a poll ballot all read the same resolved object, so none of them has to guess where an event will be, and none of them has to wait for it to start before saying so.

## What it gives you

* **The schedule as data.** One file per timeline in `plugins/EventControl/queue/`, one per event in `event/`. How often, how far ahead, out of which events. See [Writing a queue](writing-a-queue.md) and [Writing an event](writing-an-event.md).
* **Event kinds from your own jar.** A kind of event is an `EventType` registered from your `onEnable`, compiled against `eventcontrol-api` from Maven Central. The engine owns every scheduling state it used to have to keep for itself, and tears down everything it allocates. See [Extending EventControl](extending-eventcontrol.md).
* **Pluggable choice.** `sequential` and `weighted-random` ship; the always-on request channel outranks both. A source answers with a future, which is the only reason a player vote is the same kind of thing as a dice roll — **EventPoll** is exactly that, in its own jar, holding the future open for a voting window.
* **Placeholders that resolve early.** `%eventcontrol_main_next_display%`, `_starts-in%`, `_position%` and the rest, for any slot within the queue's lookahead. See [Placeholders](placeholders.md).
* **A timeline that survives a restart.** Pending slots are written to disk as they change, so a nightly restart resumes the schedule instead of starting a fresh round of events every morning.
* **Live reload.** `/eventcontrol reload` re-reads every file without a restart and without doubling a single repeating task. See [Commands and permissions](commands-and-permissions.md).

## The schedule is a file

```yaml
# plugins/EventControl/queue/main.yml

interval: 30m
prepare-lead: 5m
lookahead: 3
source: weighted-random

events:
  - demo
  - demo-wide
```

The engine keeps one invariant, and everything visible follows from it: **`lookahead` slots are always planned, and the head is always prepared by `startAt - prepare-lead`.** One tick a second advances slots through `PLANNED → PREPARING → PREPARED → RUNNING → FINISHED`, so nothing is scheduled for a future moment that a restart could lose.

## The four stages of an event

Each stage owns strictly less than the one before, and the split is the whole contract:

| Stage             | Owned by                | Carries                                                                  |
| ----------------- | ----------------------- | ------------------------------------------------------------------------ |
| `EventType`       | the plugin providing it | the key, how to read a definition, how to prepare one                    |
| `EventDefinition` | EventControl            | the configured facts: display name, duration, weight, cooldown           |
| `PreparedEvent`   | the queue engine        | **every resolved fact**, including the location and the exact start time |
| `ActiveEvent`     | the queue engine        | runtime state, participants, and anything to be torn down                |

A `PreparedEvent` is discardable: if the slot is skipped or the server stops before it starts, the engine calls `release()`.

## Registering a kind of event

```java
@Override
public void onEnable() {
    EventControlApi.getRegistries()
            .eventTypes()
            .register(new ShrineEventType(getSLF4JLogger()));
}
```

Nothing about it is second-class — the bundled kind and both built-in sources are registered through the same registries, in the same window. [Extending EventControl](extending-eventcontrol.md) covers the whole surface: the definition, preparation and its three obligations, teardown, participants, queue sources, and reading the timeline from another plugin.

## Requirements

|          |                                                                                      |
| -------- | ------------------------------------------------------------------------------------ |
| Server   | Paper 1.16 or later                                                                  |
| Java     | 16 or later                                                                          |
| Optional | [CommandAPI](https://docs.commandapi.dev/), for the commands                         |
| Optional | [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI), for placeholders |

Both optional dependencies are soft. Without CommandAPI there are no commands; without PlaceholderAPI the placeholders are not registered. The queues run either way.

## How it fits together

1. EventControl enables, registers `sequential` and `weighted-random`, and publishes its API.
2. Plugins that extend it enable next and register their event kinds and sources. `depend: [EventControl]` is what guarantees the order.
3. One tick after the server finishes enabling plugins, the registries close and EventControl reads `event/` and `queue/`. A file naming a kind nobody registered is logged and skipped, so one bad file costs you that file rather than the queue.
4. Each queue restores its persisted timeline, plans up to `lookahead` slots, and asks its source to fill them.
5. The head slot is prepared `prepare-lead` before it is due, becomes readable to placeholders and ballots, then starts on time.
6. `/eventcontrol reload` repeats steps 3 to 5. Registered kinds and sources survive; only definitions and timelines are rebuilt.

To compile the plugin yourself, see [Building from source](building-from-source.md).
