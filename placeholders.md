---
description: >-
  Every form of %eventcontrol_...%: queue fields, slot selectors, slot fields,
  display names and their markup variants.
---

# Placeholders

EventControl registers the `eventcontrol` expansion with PlaceholderAPI, so the timeline is readable from anything that renders text — a scoreboard, a hologram, a tab header, another plugin's message.

Every value comes from the in-memory timeline, never from disk: placeholders resolve on the main thread, once per viewer per frame.

There are two shapes.

```
%eventcontrol_<queue>_<field>%                a fact about a queue
%eventcontrol_<queue>_<slot>_<field>%         a fact about one slot of a queue
%eventcontrol_<event>%                        one event's display name, no queue involved
```

## Queue fields

| Placeholder                     | Gives                                             |
| ------------------------------- | ------------------------------------------------- |
| `%eventcontrol_main_running%`   | `true` or `false` — whether the queue is ticking. |
| `%eventcontrol_main_upcoming%`  | How many slots are on the timeline.               |
| `%eventcontrol_main_lookahead%` | The queue's configured lookahead.                 |
| `%eventcontrol_main_interval%`  | The queue's interval, in milliseconds.            |

## Picking a slot

| Selector | The slot                                                       |
| -------- | -------------------------------------------------------------- |
| `active` | Running now.                                                   |
| `next`   | Due next.                                                      |
| `next2`  | The one after that, and `next3`, `next4` … within `lookahead`. |

A selector with nothing behind it — `active` on an idle queue, `next3` on a `lookahead` of 2 — resolves to nothing rather than to an error.

## Slot fields

The field is optional and defaults to `name`, so `%eventcontrol_main_next%` is the key of the event due next.

| Field             | Gives                                                                                       |
| ----------------- | ------------------------------------------------------------------------------------------- |
| `name`, `key`     | The event's key, as the file names it.                                                      |
| `display`         | The event's display name, from `lang/`. See [Display names](placeholders.md#display-names). |
| `state`           | `planned`, `preparing`, `prepared`, `running`, `finished`, `cancelled`.                     |
| `origin`          | `scheduled` or `requested`.                                                                 |
| `requester`       | Who requested it, for a `requested` slot.                                                   |
| `prepared`        | `true` once the slot is fully resolved.                                                     |
| `starts-in`, `in` | Milliseconds until it starts.                                                               |
| `ends-in`         | Milliseconds until it ends.                                                                 |
| `duration`        | The event's configured length, in milliseconds.                                             |
| `x`, `y`, `z`     | Block coordinates of the event.                                                             |
| `position`        | `x, y, z` in one string.                                                                    |
| `world`           | The world the event is in.                                                                  |

{% hint style="info" %}
**An empty value and an unresolved placeholder mean different things.** A field that exists but has nothing behind it yet — `position` on a slot that is not prepared — resolves to an empty string. A field that does not exist leaves the placeholder as literal text. So `%eventcontrol_main_next_postion%` appearing verbatim on a scoreboard is a typo, while an empty gap means the slot has not been prepared yet.
{% endhint %}

Everything about location depends on preparation, and that is the point: `x`, `y`, `z`, `position` and `world` are empty until the head slot is within `prepare-lead` of starting, and exact from then on. No other plugin has to be asked, and nothing has to be guessed from the length of a string.

## Durations

Every duration is **raw milliseconds**. Formatting is not EventControl's job — a server that wants `04m 30s` points the [DateTimeFormat expansion](https://wyne.gitbook.io/wyne-docs/datetimeformatexpansion/) at the value:

```
%dtf_duration_{eventcontrol_main_next_starts-in}ms_mm'm 'ss's'%
```

The same applies inside EventControl's own language files, which is why they read `%dtf_duration_<starts-in>ms_…%` rather than a pre-rendered countdown. Change the pattern there and every message that shows a countdown changes with it.

## Display names

A display name is the one value that comes out of a language file rather than the timeline, so it carries markup — and the reader decides which dialect it arrives in.

On a slot the variant is a suffix of the field:

| Placeholder                               | Gives                                             |
| ----------------------------------------- | ------------------------------------------------- |
| `%eventcontrol_main_next_display%`        | The name in the configured `serializer`'s markup. |
| `%eventcontrol_main_next_display-legacy%` | `&`-coded legacy.                                 |
| `%eventcontrol_main_next_display-plain%`  | No markup at all.                                 |
| `%eventcontrol_main_next_display-mm%`     | MiniMessage.                                      |
| `%eventcontrol_main_next_display-gson%`   | The JSON component.                               |

Addressed by event key instead, the variant is a separate argument:

```
%eventcontrol_demo%             the display name
%eventcontrol_demo_legacy%      the same name, as &-codes
%eventcontrol_demo-wide_plain%  an event whose key contains a hyphen
```

{% hint style="info" %}
The two forms differ deliberately. An event key may itself contain a hyphen — `demo-wide` does — so `%eventcontrol_demo-legacy%` would be ambiguous between the event `demo-legacy` and the event `demo` rendered as legacy. A separate argument cannot collide, because `_` is already forbidden in a key.
{% endhint %}

The variants exist for plugins that parse a different dialect than this server's `serializer`. A plugin reading legacy codes asks for `display-legacy` and gets a name it can render, whatever the language files are written in.

## When a key names both

Queues and events have separate namespaces, so the same key can name one of each. A bare key resolves the **queue**, which makes that event unreachable by key alone. EventControl logs it once at load rather than leaving it to be discovered:

```
Event 'x' shares its key with a queue, so a placeholder addressed by
that key alone resolves to the queue
```

The event is still reachable through any queue that runs it.

## A worked example

A scoreboard line that reads "Next: Demo event in 04m 30s" and goes quiet when nothing is scheduled:

```
Next: %eventcontrol_main_next_display% in %dtf_duration_{eventcontrol_main_next_starts-in}ms_mm'm 'ss's'%
```

And one that only says anything while an event is live:

```
%eventcontrol_main_active_display% at %eventcontrol_main_active_position%
```
