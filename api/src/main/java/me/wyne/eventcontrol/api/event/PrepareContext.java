package me.wyne.eventcontrol.api.event;

import me.wyne.eventcontrol.api.queue.QueueSlot;
import me.wyne.eventcontrol.api.queue.QueueView;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * Handed to {@link EventType#prepare} so a type can resolve its placement
 * without owning any scheduling state.
 */
public interface PrepareContext {

    @NotNull QueueView getQueue();

    @NotNull QueueSlot getSlot();

    @NotNull World getWorld();

    /** When the prepared event is scheduled to start. */
    @NotNull Instant getStartAt();

    /**
     * Milliseconds left before the engine wanted this preparation finished.
     * A long search should treat this as its budget.
     */
    long getRemainingLeadMillis();

    /**
     * An executor for off-main-thread work. Preparation runs off the main
     * thread; anything touching the Bukkit API must hop back itself.
     */
    @NotNull Executor getAsyncExecutor();

    /**
     * True once the engine has given up on this slot. A long-running search
     * must poll this and abort, and anything already allocated must be released.
     */
    boolean isCancelled();

    /**
     * Registers a callback invoked if the slot is cancelled while preparation
     * is still in flight.
     */
    void onCancel(@NotNull Runnable callback);
}
