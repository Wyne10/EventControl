package me.wyne.eventcontrol.api.bukkit;

import me.wyne.eventcontrol.api.queue.QueueSlot;
import me.wyne.eventcontrol.api.queue.QueueView;
import me.wyne.eventcontrol.api.queue.SlotState;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a slot moved through the state machine:
 * {@code PLANNED → PREPARING → PREPARED → RUNNING → FINISHED}, or to
 * {@code CANCELLED} from anywhere before the end.
 *
 * <p>Fired after the move, so the slot already reports the new state. The two
 * transitions worth most to a listener are the one into {@code PREPARED} —
 * where {@link QueueSlot#getPrepared()} first has a location and a settled start
 * time — and the one into {@code RUNNING}.
 *
 * <p>{@code CANCELLED} means the event will not happen and whatever its
 * preparation allocated has been released; {@code FINISHED} means it ran to its
 * end. Both are terminal, and the engine drops the slot immediately after this
 * event, so a listener must not hold onto it.
 *
 * <p>On either terminal state the slot has already let go of its
 * {@link QueueSlot#getPrepared()} and {@link QueueSlot#getActive()}, so a
 * listener that needs to know what ended should read
 * {@link QueueSlot#getDefinition()}, which survives, or keep what it saw on the
 * way into {@code PREPARED}.
 */
public class QueueSlotStateChangeEvent extends QueueSlotEvent {

    private final SlotState previousState;
    private final SlotState state;

    public QueueSlotStateChangeEvent(@NotNull QueueView queue, @NotNull QueueSlot slot,
                                     @NotNull SlotState previousState, @NotNull SlotState state) {
        super(queue, slot);
        this.previousState = previousState;
        this.state = state;
    }

    /** The state the slot was in before this change. */
    public @NotNull SlotState getPreviousState() {
        return previousState;
    }

    /** The state the slot is in now. */
    public @NotNull SlotState getState() {
        return state;
    }

}
