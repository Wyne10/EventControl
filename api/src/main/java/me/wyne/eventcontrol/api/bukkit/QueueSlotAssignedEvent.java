package me.wyne.eventcontrol.api.bukkit;

import me.wyne.eventcontrol.api.event.EventDefinition;
import me.wyne.eventcontrol.api.queue.QueueSlot;
import me.wyne.eventcontrol.api.queue.QueueView;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a slot learns what it will run — the configured source chose, or an
 * admin requested it. {@link QueueSlot#getOrigin()} says which.
 *
 * <p>This is not a state change: the slot stays {@code PLANNED}. It is the
 * earliest point at which the upcoming event is known by name, which is what
 * makes a lookahead worth showing, and it fires well before
 * {@link QueueSlotStateChangeEvent} reports {@code PREPARED}.
 *
 * <p>A slot may be assigned more than once over its life if its first
 * preparation failed and it was replanned.
 */
public class QueueSlotAssignedEvent extends QueueSlotEvent {

    private final EventDefinition definition;

    public QueueSlotAssignedEvent(@NotNull QueueView queue, @NotNull QueueSlot slot, @NotNull EventDefinition definition) {
        super(queue, slot);
        this.definition = definition;
    }

    /** What the slot will run. */
    public @NotNull EventDefinition getDefinition() {
        return definition;
    }

}
