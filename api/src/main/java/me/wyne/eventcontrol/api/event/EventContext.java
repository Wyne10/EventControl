package me.wyne.eventcontrol.api.event;

import me.wyne.eventcontrol.api.queue.QueueSlot;
import me.wyne.eventcontrol.api.queue.QueueView;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Handed to {@link PreparedEvent#start} and available to a running event.
 */
public interface EventContext {

    @NotNull QueueView getQueue();

    @NotNull QueueSlot getSlot();

    /**
     * Registers something to be closed when the event ends, however it ends.
     * Listeners, tasks, spawned entities and pasted structures go here instead
     * of into hand-written teardown.
     */
    <T extends AutoCloseable> @NotNull T bind(@NotNull T closeable);

    /**
     * Players currently counted as taking part. EventControl only keeps the
     * set; what it means and who goes in it is the owning plugin's call.
     */
    @NotNull Collection<Player> getParticipants();

    void addParticipant(@NotNull Player player);

    /**
     * Ends the event now rather than waiting out its duration. The engine runs
     * the normal end path: {@link ActiveEvent#onEnd()}, teardown of everything
     * bound here, and the next slot.
     */
    void endNow();

}
