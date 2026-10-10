package me.wyne.eventcontrol.api.bukkit;

import me.wyne.eventcontrol.api.queue.QueueSlot;
import me.wyne.eventcontrol.api.queue.QueueView;
import org.jetbrains.annotations.NotNull;

/**
 * Something that happened to one position on a timeline.
 *
 * <p>The slot is the engine's live object, so reading it gives current state
 * rather than a snapshot taken when the event fired.
 */
public abstract class QueueSlotEvent extends QueueEvent {

    private final QueueSlot slot;

    protected QueueSlotEvent(@NotNull QueueView queue, @NotNull QueueSlot slot) {
        super(queue);
        this.slot = slot;
    }

    /** The slot this happened to. */
    public @NotNull QueueSlot getSlot() {
        return slot;
    }

}
