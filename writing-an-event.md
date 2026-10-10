---
description: >-
  One file per event: which registered kind it is, what it is called, how long
  it lasts, and how often it may be chosen.
---

# Writing an event

An event file says _which kind of event this is_ and _the facts that make this one different from the others of its kind_. One file in `plugins/EventControl/event/` is one event, and the file name is its key.

```yaml
# plugins/EventControl/event/demo.yml

type: demo
display: event-demo
duration: 10m
cooldown: 0s
weight: 1

world: world
radius: 200
```

The first five keys are read by EventControl. Everything after them belongs to the kind named by `type` — `world` and `radius` here are the `demo` kind's own, and a different kind reads whatever it documents.

| Key        | Default       | Means                                                                                                   |
| ---------- | ------------- | ------------------------------------------------------------------------------------------------------- |
| `type`     | — required    | Which registered kind of event this is.                                                                 |
| `display`  | `event-<key>` | The language key holding this event's name. See [Naming an event](writing-an-event.md#naming-an-event). |
| `duration` | `10m`         | How long the event runs once started.                                                                   |
| `cooldown` | `0s`          | The minimum gap before this event may be selected again.                                                |
| `weight`   | `1`           | Relative weight for `weighted-random`. `0` leaves it available only by request or poll.                 |

Two files can name the same `type` and differ only in their own keys, which is how one kind of event becomes several events:

```yaml
# plugins/EventControl/event/demo-wide.yml

type: demo
display: event-demo-wide
duration: 10m
weight: 1

radius: 600
```

Both are then eligible in a queue, as separate entries with separate weights and cooldowns.

{% hint style="warning" %}
An event key must match `[a-z0-9-]+`, for the same reason a queue key must: PlaceholderAPI splits on `_`. Keep the files flat as well — a file in a subdirectory takes the directory into its key, and the `/` fails the rule. A file that fails to load is logged by name and skipped, so a bad file costs you that event rather than the queue.
{% endhint %}

## Naming an event

`display` is a key in EventControl's own `lang/` files:

```yaml
# plugins/EventControl/lang/en.yml
event-demo: "<aqua>Demo event"
event-demo-wide: "<aqua>Demo event (wide search)"
```

Omit `display` and it defaults to `event-<key>`, which is why the shipped files and the shipped language files line up without saying so.

This holds for an event kind that came from another plugin too. The _definition file_ is EventControl's, so the key written in it points into EventControl's language files, not the providing plugin's. A plugin that ships an event kind tells you the key it expects; you write the name, in the languages you serve. An unresolved key renders as its own name, so a player reading `event-shrine` means that key is missing from `lang/`.

The name is then readable anywhere as `%eventcontrol_<key>%`, and from a queue as `%eventcontrol_<queue>_next_display%` — see Placeholders.

## `duration`, `cooldown` and `interval`

Three durations decide how often an event actually happens, and they do different jobs:

* **`duration`** is this event's own length. The engine ends it when it elapses, and an event kind may end itself earlier.
* **`cooldown`** is this event's own minimum spacing. After it ends, the queue will not start it again until the cooldown has passed — and because the queue lays its timeline out ahead, a cooldown longer than the `interval` pushes the _next slot_ back rather than just skipping this event.
* **`interval`** is the [queue's](writing-a-queue.md) gap between consecutive events, whichever they are.

The gap a queue leaves after an event is the larger of its `interval` and that event's `cooldown`.

Durations are written as `30s`, `5m`, `1h30m`, `2d`, or `150ms`. A bare number is read as seconds.

## `weight`

`weight` only means something to a source that rolls. For `weighted-random`, an event's share of the rotation is its weight over the total of the eligible events' weights — `weight: 3` against `weight: 1` is three times as often.

`weight: 0` takes an event out of the rotation without taking it out of the queue. It is still listed under `events`, still eligible for `/eventcontrol request`, and still a candidate for a poll. That is the way to keep a once-a-month event configured and addressable without it ever coming up by chance.

## The bundled kind

The `demo` kind ships in the jar, and exists so a fresh install has something to run and so there is a worked example of the one contract that matters: every fact is settled before the event starts. It reads two keys of its own:

| Key      | Default                  | Means                                  |
| -------- | ------------------------ | -------------------------------------- |
| `world`  | the server's first world | Which world to pick a location in.     |
| `radius` | `200`                    | How far from spawn to look, in blocks. |

It resolves a location during preparation and does nothing else — no structure, no mobs, no rewards. Replace it as soon as you have a real event kind installed; a queue whose `events` list is empty is rejected at load, so delete the shipped files only once something else is in there.

Note what the `demo` kind does _not_ get from the engine: the world is its own key, read by it, not a queue setting. A queue never decides where an event happens, because only the event knows what a valid place for it looks like.
