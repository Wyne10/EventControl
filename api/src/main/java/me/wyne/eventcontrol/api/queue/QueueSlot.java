package me.wyne.eventcontrol.api.queue;

import me.wyne.eventcontrol.api.event.ActiveEvent;
import me.wyne.eventcontrol.api.event.EventDefinition;
import me.wyne.eventcontrol.api.event.PreparedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * One position on a queue's timeline. Read-only: mutation goes through
 * {@link QueueView}'s named operations.
 */
public interface QueueSlot {

    @NotNull SlotState getState();

    @NotNull SlotOrigin getOrigin();

    /** Who asked for this slot, when the origin names someone. */
    @Nullable String getRequester();

    /** Null only while a source is still deciding. */
    @Nullable EventDefinition getDefinition();

    /** When this slot is due to start. */
    @NotNull Instant getStartAt();

    /** Non-null from {@link SlotState#PREPARED} until the slot is terminal. */
    @Nullable PreparedEvent getPrepared();

    /** Non-null only while {@link SlotState#RUNNING}. */
    @Nullable ActiveEvent getActive();

}
