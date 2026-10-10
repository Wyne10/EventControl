---
description: >-
  One file per timeline: how often events run, how far ahead they are resolved,
  which events are eligible, and what chooses between them.
---

# Writing a queue

A queue is a timeline. One file in `plugins/EventControl/queue/` is one of them, and a server can run as many as it has use for — a queue per world, a queue per kind of event, a fast queue and a slow one.

```yaml
# plugins/EventControl/queue/main.yml

interval: 30m
prepare-lead: 5m
lookahead: 3

source: weighted-random
weighted-random-avoid-repeats: 1

auto-start: true

events:
  - demo
  - demo-wide
```

The file name is the queue's key — `main.yml` is the queue `main`, which is how placeholders and commands address it.

| Key            | Default           | Means                                                                                |
| -------------- | ----------------- | ------------------------------------------------------------------------------------ |
| `interval`     | `30m`             | The gap between one event ending and the next starting.                              |
| `prepare-lead` | `5m`              | How long before its start time the head slot is resolved.                            |
| `lookahead`    | `3`               | How many slots are kept planned ahead. At least 1.                                   |
| `source`       | `weighted-random` | What chooses the event for an empty slot. See [Sources](writing-a-queue.md#sources). |
| `auto-start`   | `true`            | Whether the queue starts ticking as soon as it loads.                                |
| `events`       | — required        | The keys of the events this queue may select, from `event/`.                         |

Anything else in the file is read by the source, not by the engine — `weighted-random-avoid-repeats` above is one of those.

{% hint style="warning" %}
A queue key must match `[a-z0-9-]+`. PlaceholderAPI splits on `_`, so an underscore would make the queue unaddressable; the key is rejected at load with a logged error instead of failing quietly later. Keep the files flat, too — a queue in a subdirectory takes the directory into its key, and the `/` fails the same rule.
{% endhint %}

## How the timeline moves

One tick a second advances every slot. A slot's state is the whole scheduling story, and it replaces the per-plugin maps each event used to keep for itself:

| State       | Means                                                         |
| ----------- | ------------------------------------------------------------- |
| `PLANNED`   | On the timeline with a start time; may not have an event yet. |
| `PREPARING` | The event kind is resolving it, off the main thread.          |
| `PREPARED`  | Fully resolved and waiting for its start time.                |
| `RUNNING`   | Live in the world.                                            |
| `FINISHED`  | Ran and ended normally.                                       |
| `CANCELLED` | Never ran: skipped, replaced, or its preparation failed.      |

The engine holds one invariant: **`lookahead` slots are always planned, and the head is always prepared by `startAt - prepare-lead`.** Everything visible follows from it. Nothing is scheduled for a future moment that a restart could lose — each tick simply asks what should be true now.

A planned slot's start time moves as the timeline tightens: a slot is planned before its source has chosen anything, so its length is a guess until then. **A prepared slot's start time never moves.** By then it may already have been shown to players, and shifting it would make that a lie.

## Sizing `prepare-lead`

`prepare-lead` is the budget an event kind gets to resolve itself, and the window in which a planned event becomes visible in advance. Two rules:

* **It must comfortably exceed the slowest preparation.** An event kind that searches the world for a valid location can take tens of seconds; it is handed `getRemainingLeadMillis()` as its budget and is expected to respect it, but a lead shorter than the search will cancel slots.
* **It is how far ahead players can see.** `%eventcontrol_main_next_position%` resolves only once the head slot is prepared, so a one-minute lead means one minute of warning, however large `lookahead` is.

Nothing starts sooner than it can be prepared. A queue that has just loaded sets its first start time to `now + prepare-lead` rather than running an event immediately.

## `lookahead`

`lookahead` is how many slots exist at all. Only the head is prepared; the rest are planned, with an event chosen and a start time, which is enough to name them:

```
%eventcontrol_main_next_display%    the head
%eventcontrol_main_next2_display%   the one after it
%eventcontrol_main_next3_display%   and so on, within lookahead
```

It also governs how often a source is asked: every empty planned slot is offered to the source, so a `lookahead` of 3 means three questions, not one. That matters for a source with a user-visible side effect — see writing a source.

## Sources

| `source`          | Chooses                                                |
| ----------------- | ------------------------------------------------------ |
| `weighted-random` | A weighted roll over the eligible events. The default. |
| `sequential`      | The next event in the order `events` lists them.       |

`weighted-random` reads one key of its own:

```yaml
source: weighted-random
weighted-random-avoid-repeats: 1
```

`weighted-random-avoid-repeats` is how many recently chosen events to pass over, so a two-event queue with `1` alternates instead of repeating itself. It is a preference rather than a rule, and it caps itself at one less than the number of eligible events, so it can never exclude everything and stall the queue. Set it to `0` to allow immediate repeats.

An event with `weight: 0` is never rolled. It stays available to an admin request and to a poll, which is how a rare event is kept out of the rotation without being removed from `events`.

Other sources come from other plugins and are configured in exactly the same place — the key the plugin registered goes in `source`, and its own keys sit beside it, prefixed with that key:

```yaml
source: poll
poll-window: 2m
poll-candidates: 3
```

## Starting and stopping

`auto-start: false` leaves a queue loaded but idle until `/eventcontrol start <queue>`. Stopping one ends whatever it is running and forgets where it was in the timeline; starting it again begins a fresh one, `prepare-lead` out.

A stopped queue still answers placeholders — `%eventcontrol_main_running%` is how a scoreboard says so.

## Surviving a restart

Each queue's pending slots are written to `queues.json` as the timeline changes:

```json
{
  "main": [
    { "startAtEpochMilli": 1791669841083, "origin": "SCHEDULED" },
    { "startAtEpochMilli": 1791669921083, "origin": "SCHEDULED" }
  ]
}
```

Only slots that have not run are kept — a running event cannot survive a shutdown, so it is ended properly instead of being half-restored. On the way back up each queue resumes from those start times rather than beginning its timeline again, which is what stops a nightly restart from turning into a fresh round of events every morning.

An event key that no longer exists is dropped from the restored timeline, and the slot is refilled by the source.
