---
description: >-
  Compile against eventcontrol-api, register event kinds and queue sources from
  onEnable, read the timeline, and listen for what the engine decides.
---

# Extending EventControl

EventControl runs events; it does not contain any. A kind of event, and the thing that decides which one runs next, are both registered from outside — through the same registries the built-ins use, against the same interfaces, in the same window.

That is the whole API. There are two registries, and everything else is reading.

## Adding the dependency

The API artifact is published to Maven Central:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("io.github.wyne10:eventcontrol-api:1.0.0")
}
```

```yaml
# plugin.yml
depend: [EventControl]
```

Keep it `compileOnly`. The API classes ship inside the EventControl plugin jar under their real package names, so shading your own copy leaves you with two unrelated `EventType` interfaces and a `ClassCastException`.

Use `depend` rather than `softdepend` unless your plugin is useful without EventControl — the registries do not exist until EventControl has enabled.

Everything in `me.wyne.eventcontrol.api` is plain Java against the Bukkit API, `java.time` and `CompletableFuture`. No WUtils type appears in a published signature, because EventControl relocates WUtils into its own shadow package.

## When to register

EventControl reads its event and queue files **one tick after the server finishes enabling plugins**. That is late enough for every `depend: [EventControl]` plugin to have had its `onEnable`, and it is the window you register in:

```java
@Override
public void onEnable() {
    EventControlRegistries registries = EventControlApi.getRegistries();
    if (registries == null) {
        getLogger().severe("EventControl is not installed");
        return;
    }
    registries.eventTypes().register(new ShrineEventType(getSLF4JLogger()));
}
```

After that moment the registries close. A registration arriving later is refused with a logged warning, because the definitions that would have named your key have already been read. `registries.isOpen()` tells you which side of the line you are on.

A reload does not reopen them, and does not need to: registered kinds and sources survive, and only the definitions and timelines bound to them are rebuilt.

## The registries

| Registry         | Adds                                      | Kind     |
| ---------------- | ----------------------------------------- | -------- |
| `eventTypes()`   | A kind of event.                          | instance |
| `queueSources()` | A way of deciding what a queue runs next. | factory  |

An **instance registry** takes one shared object that carries its own key and serves every event file naming it. A **factory registry** takes a key and a factory, and builds a fresh object for each queue that uses the key — which is what lets two queues run the same source with different settings.

A factory receives the **enclosing** section, not the value at the key:

```java
public interface ConfigFactory<T> {
    @NotNull T create(@NotNull String key, @NotNull ConfigurationSection config);
}
```

That is deliberate. It lets one factory accept `source: poll` alongside sibling keys like `poll-window`, and read as many of them as it needs.

## Writing an event kind

The four stages of an event each own strictly less than the one before, and the split is the contract: **everything knowable in advance is settled in `prepare`, so it can be shown before the event starts.**

`AbstractEventType` handles the rest — the off-thread hop, the cancellation bookkeeping, the lead timer:

```java
public final class ShrineEventType extends AbstractEventType {

    public ShrineEventType(Logger logger) {
        super("shrine", logger);
    }

    @Override
    public EventDefinition readDefinition(String key, ConfigurationSection config) {
        return new ShrineDefinition(key, this, config);
    }

    @Override
    protected PreparedEvent prepareBlocking(EventDefinition definition, PrepareContext context) {
        ShrineDefinition shrine = (ShrineDefinition) definition;
        Location where = findClearing(shrine, context);
        return new PreparedShrine(shrine, where, context.getStartAt());
    }
}
```

### The definition

`SimpleEventDefinition` reads the four facts every event has — display key, duration, cooldown, weight. Extend it and add your own, from the same section:

```java
final class ShrineDefinition extends SimpleEventDefinition {

    private final int radius;

    ShrineDefinition(String key, EventType type, ConfigurationSection config) {
        super(key, type, config);
        this.radius = config.getInt("radius", 200);
    }

    int getRadius() {
        return radius;
    }
}
```

A definition is immutable and lives from one reload to the next. Anything that changes per run belongs in the prepared event.

### Preparing

`prepareBlocking` runs off the main thread, and it is the only place slow work belongs:

```java
private Location findClearing(ShrineDefinition shrine, PrepareContext context) {
    long budget = context.getRemainingLeadMillis();
    long deadline = System.currentTimeMillis() + budget;

    while (System.currentTimeMillis() < deadline) {
        if (context.isCancelled()) throw new IllegalStateException("Slot cancelled");
        // ... and anything touching the Bukkit API hops back to the main thread.
    }
    throw new IllegalStateException("No clearing found within " + budget + "ms");
}
```

Three obligations:

* **Respect the budget.** `getRemainingLeadMillis()` is how long until the engine wanted this finished. Overrunning it does not break the queue, but it eats into the warning players were going to get.
* **Poll `isCancelled()`.** A skipped or replaced slot cancels its preparation; a long search that ignores this keeps working for an event nobody will see. `onCancel(Runnable)` is there for the same reason when a callback fits better.
* **Hop to the main thread for Bukkit.** `getAsyncExecutor()` is the executor the engine uses; the Bukkit API is not thread-safe, and a world lookup from a worker thread is a crash waiting for load.

A future that completes exceptionally fails the slot, which is logged and replanned — so throwing out of `prepareBlocking` is a legitimate way to say "not this time".

### Starting, ending, releasing

```java
final class PreparedShrine extends AbstractPreparedEvent {

    @Override
    protected ActiveEvent onStart(EventContext context) {
        // Anything bound here is closed when the event ends, however it ends.
        context.bind(structure.spawn());

        BukkitTask ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
        context.bind(ticker::cancel);

        return new ActiveShrine(this, context);
    }

    @Override
    protected void onRelease() {
        // The event will never start: the slot was skipped, replaced, or the
        // server is stopping. Roll back anything prepare allocated.
        structure.close();
    }
}
```

`release()` is the half people forget. A preparation that pasted terrain, reserved a region or spawned a hologram has to undo it when the slot is skipped — and `/eventcontrol skip` exists, so it will be.

`bind` takes anything `AutoCloseable` and hands it back, so it reads inline; a Bukkit task becomes closeable as a method reference. It is the whole teardown story, and there is no path out of a running event that skips it — ending naturally, being stopped, being reloaded, or the server shutting down.

`AbstractActiveEvent` gives a running event `endNow()` for ending early and `onEnded()` for work that must happen before teardown. Participants are tracked by whoever knows what taking part means:

```java
context.addParticipant(player);
Collection<Player> taking_part = context.getParticipants();
```

EventControl only keeps the set. What it means is yours.

## Writing a queue source

A source is one method. It returns a future, which is the only reason a player vote is the same kind of thing as a dice roll:

```java
public final class RotationSource implements QueueSource {

    @Override
    public CompletableFuture<EventDefinition> nextFor(QueueView queue, QueueSlot slot) {
        return CompletableFuture.completedFuture(pick(queue.getCandidates()));
    }

    @Override
    public void reset() {
        // The queue reloaded; drop cached state.
    }
}
```

```java
registries.queueSources().register("rotation", (key, config) ->
        new RotationSource(config.getBoolean(key + "-reverse", false)));
```

The future may complete long after the call — a poll holds it open for its voting window — but it must complete before the slot's start time, or the engine cancels the slot and replans. Completing with `null` means "nothing to run", which leaves the slot empty for a later tick rather than failing the queue.

The request channel is always present and outranks the source, so a source never has to account for admin requests.

{% hint style="warning" %}
**A queue asks about every empty slot, not just the next one.** With `lookahead: 3` a fresh queue puts the same question three times in the same tick. For a roll that is fine; for a source with a user-visible side effect it is three ballots at once. Guard it:

```java
if (hasOpenBallot(queue.getKey()))
    return CompletableFuture.completedFuture(null);
```

Declining leaves the slot empty, the engine asks again next tick, and the next ballot opens once this one has closed.
{% endhint %}

## Reading the timeline

Reading needs no registration and no timing care, only `depend`:

```java
EventControlProvider provider = EventControlApi.getProvider();

for (QueueView queue : provider.getQueues()) {
    QueueSlot next = queue.getNextSlot();
    if (next == null) continue;

    PreparedEvent prepared = next.getPrepared();
    if (prepared != null) {
        // Resolved: the location and the exact start time are real.
        announce(prepared.getDefinition(), prepared.getLocation(), prepared.getStartAt());
    }
}
```

Every getter returns an immutable snapshot, never the engine's live collections. `getPrepared()` is null until the slot is within `prepare-lead` of starting, and that null is the one thing worth branching on — it is the honest answer to "where will it be", asked too early.

`provider.getDefinition(key)` and `getDefinitions()` reach the configured events without going through a queue.

## Listening for changes

Four Bukkit events fire on the main thread as the engine decides things:

| Event                       | Fires when                                      |
| --------------------------- | ----------------------------------------------- |
| `QueueSlotAssignedEvent`    | A source or a request has filled a slot.        |
| `QueueSlotStateChangeEvent` | A slot moves between states.                    |
| `QueueRunningChangeEvent`   | A queue starts or stops.                        |
| `QueueEvent`                | The abstract base — listen to it for all three. |

```java
@EventHandler
public void onState(QueueSlotStateChangeEvent event) {
    if (event.getState() == SlotState.PREPARED)
        announce(event.getSlot().getPrepared());
}
```

They share one handler list, so a listener taking the base type sees every subclass and one taking a concrete subclass sees only that. None of them is `Cancellable`: they report what has already been decided.

{% hint style="info" %}
**A server stop is not observable this way.** Bukkit disables dependent plugins before the plugin they depend on, and skips a disabled plugin's listeners, so a plugin that `depend`s on EventControl is already gone by the time the queues shut down. Do shutdown work in your own `onDisable`.
{% endhint %}

## Checking your work

* `registries.eventTypes().keys()` and `queueSources().keys()` list what is registered, including your additions.
* An event file naming an unregistered kind logs `Event '<key>' names unknown type '<type>'` with the list of registered types — which usually means the registration arrived late, or never ran.
* A queue naming an unregistered source fails the same way, with the registered sources listed.
* [`logLevel: DEBUG`](configuration.md) logs each definition as it loads, each slot transition, and what a source answered.
