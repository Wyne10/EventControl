package me.wyne.eventcontrol.api.bukkit;

import me.wyne.eventcontrol.api.queue.QueueView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Something a queue did, for plugins that want to watch a timeline they do not
 * own — an announcer, a scoreboard, a poll plugin following the slot it filled.
 *
 * <p>Every subclass shares this handler list, so a listener taking a
 * {@code QueueEvent} sees all of them and one taking a concrete subclass sees
 * only that kind. The event an {@link me.wyne.eventcontrol.api.event.EventType}
 * owns is better observed through its own
 * {@link me.wyne.eventcontrol.api.event.PreparedEvent} and
 * {@link me.wyne.eventcontrol.api.event.ActiveEvent}; these exist for everyone
 * else.
 *
 * <p>These are reports, not decisions: none of them is {@code Cancellable}. The
 * engine re-derives the timeline from current state once a second, so a vetoed
 * transition would simply happen again on the next tick. To stop something, use
 * the engine's own operations.
 *
 * <p>Always fired on the main thread.
 *
 * <p><b>Shutdown is not observable.</b> Bukkit disables dependent plugins before
 * the plugin they depend on, and skips listeners belonging to an already
 * disabled plugin — so a plugin that depends on EventControl is down before
 * EventControl stops its queues, and never receives the events that stop
 * produces. Clean up through {@code EventContext.bind}, which the engine closes
 * directly, or in the plugin's own {@code onDisable}. These events are for a
 * running server.
 */
public abstract class QueueEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final QueueView queue;

    protected QueueEvent(@NotNull QueueView queue) {
        this.queue = queue;
    }

    /** The queue this happened on. */
    public @NotNull QueueView getQueue() {
        return queue;
    }

    /** The static handler list Bukkit uses to register listeners for these events. */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

}
