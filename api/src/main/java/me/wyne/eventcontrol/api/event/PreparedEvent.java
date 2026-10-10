package me.wyne.eventcontrol.api.event;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A fully resolved event that has not started yet.
 *
 * <p>This is the type everything readable in advance reads from: the queue
 * placeholders, the upcoming-events screen, a poll ballot and whatever the
 * owning plugin tells players ahead of time. Whatever is only knowable at start
 * time does not belong here.
 */
public interface PreparedEvent {

    @NotNull EventDefinition getDefinition();

    /** Where the event will happen, or null for an event with no single place. */
    @Nullable Location getLocation();

    @NotNull Instant getStartAt();

    @NotNull Instant getEndAt();

    /**
     * Starts the event. The returned instance owns the runtime state.
     */
    @NotNull ActiveEvent start(@NotNull EventContext context);

    /**
     * Discards everything this preparation allocated, because the event will
     * never start — the slot was skipped, cancelled, or the server is shutting
     * down. A type that pre-pasted terrain rolls it back here.
     */
    default void release() {
    }
}
