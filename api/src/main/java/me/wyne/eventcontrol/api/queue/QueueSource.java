package me.wyne.eventcontrol.api.queue;

import me.wyne.eventcontrol.api.event.EventDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Decides what fills a queue's next planned slot. A rotation, a weighted roll
 * and a player poll are all the same kind of thing.
 *
 * <p>The request channel is always present and outranks the configured source,
 * so an implementation never has to account for admin requests.
 */
public interface QueueSource {

    /**
     * Chooses a definition for {@code slot}. The future may complete later than
     * the call — a poll holds it open for the voting window — but must complete
     * before the slot's start time, or the engine cancels the slot and replans.
     *
     * <p>Completing with {@code null} means "nothing to run", which leaves the
     * slot empty rather than failing the queue.
     */
    @NotNull CompletableFuture<EventDefinition> nextFor(@NotNull QueueView queue, @NotNull QueueSlot slot);

    /** Called when the queue reloads, so a source can drop cached state. */
    default void reset() {
    }
}
