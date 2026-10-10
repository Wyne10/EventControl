---
description: >-
  The /eventcontrol admin command, the player-facing /event, and the permission
  nodes that guard them.
---

# Commands and permissions

`/eventcontrol`, aliased `/ec`, is the admin side. `/event` is what players use. Both need [CommandAPI](https://commandapi.jorel.dev/) — it is a soft dependency, and without it the plugin runs its queues but registers no commands.

Every argument is tab-completed from live state: queue names from the loaded queues, event names from the loaded definitions.

## Inspecting

| Command                      | Does                                                                       |
| ---------------------------- | -------------------------------------------------------------------------- |
| `/eventcontrol queues`       | Lists every queue, whether it is running, and how many slots it has.       |
| `/eventcontrol info <queue>` | The whole timeline: each slot's event, state, origin, countdown and place. |

`info` is the one to reach for when a queue is not doing what you expect. A slot stuck in `preparing` means an event kind is still searching; a slot in `planned` with no event means its source has not answered yet.

## Controlling

| Command                                 | Does                                                                                   |
| --------------------------------------- | -------------------------------------------------------------------------------------- |
| `/eventcontrol start <queue>`           | Starts a stopped queue. The first event is scheduled `prepare-lead` out.               |
| `/eventcontrol stop <queue>`            | Stops the queue and ends whatever it is running.                                       |
| `/eventcontrol skip <queue>`            | Drops the head slot, releasing its preparation — or ends it, if it is already running. |
| `/eventcontrol request <queue> <event>` | Puts an event at the head of the timeline.                                             |
| `/eventcontrol reload`                  | Re-reads the config, the language files and every queue and event file.                |

`request` is how an event runs on purpose rather than by chance. It outranks whatever the queue's source would have chosen, and it works for an event with `weight: 0`, which is the usual way to keep a special event configured but out of the rotation.

{% hint style="info" %}
Requesting discards slots that were already prepared, rather than shifting them. A prepared slot's start time may already have been shown to players through a placeholder or an announcement, so the engine would rather resolve a fresh one than quietly move an event somebody was told about.
{% endhint %}

`skip` cancels properly: the slot's `release()` runs, so an event kind that had already pasted terrain or reserved a region rolls it back. It advances the timeline by exactly one position.

## For players

| Command           | Does                                                                              |
| ----------------- | --------------------------------------------------------------------------------- |
| `/event`          | What is running now and what is coming, per running queue, with places and times. |
| `/event upcoming` | The same thing, named explicitly.                                                 |

`/event` shows the running event with its location and time remaining, then each planned slot with its countdown. A slot that is not prepared yet shows its event and its time but no place, because there is not one yet — which is the honest answer rather than a guess.

The wording comes from `lang/`, so the whole screen is yours to rewrite; see [Configuration](configuration.md#languages).

## Permissions

| Node                         | Default | Grants                                 |
| ---------------------------- | ------- | -------------------------------------- |
| `eventcontrol.event`         | all     | `/event`.                              |
| `eventcontrol.queue.view`    | op      | `queues` and `info`.                   |
| `eventcontrol.queue.control` | op      | `start`, `stop`, `skip` and `request`. |
| `eventcontrol.reload`        | op      | `reload`.                              |
| `eventcontrol.*`             | op      | The three admin nodes above.           |

`eventcontrol.event` defaults to everyone, since it only shows what the server is about to do anyway. The rest default to OP.

The split between `view` and `control` is there so a moderator can be given the timeline without being given the ability to skip an event out of it.
