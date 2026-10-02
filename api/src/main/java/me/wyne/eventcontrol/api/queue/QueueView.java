package me.wyne.eventcontrol.api.queue;

import me.wyne.eventcontrol.api.event.EventDefinition;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * A queue's timeline. Every getter returns an immutable snapshot — callers
 * never receive the engine's live collections.
 */
public interface QueueView {

    @NotNull String getKey();

    @NotNull World getWorld();

    @NotNull Duration getInterval();

    /** How long before its start time the head slot must be prepared. */
    @NotNull Duration getPrepareLead();

    /** How many slots are kept planned ahead, so they can be shown in advance. */
    int getLookahead();

    boolean isRunning();

    /**
     * The upcoming slots, soonest first, excluding anything terminal. The first
     * entry is prepared once it is within {@link #getPrepareLead()} of starting.
     */
    @NotNull List<QueueSlot> getUpcoming();

    /** The slot running now, if any. */
    @Nullable QueueSlot getActiveSlot();

    /** The slot due next, equivalent to the first entry of {@link #getUpcoming()}. */
    @Nullable QueueSlot getNextSlot();

    /** Every definition this queue may select. */
    @NotNull List<EventDefinition> getCandidates();
}
