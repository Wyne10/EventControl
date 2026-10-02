package me.wyne.eventcontrol.api.queue;

/**
 * Where a slot is in its life. The engine owns every transition; this replaces
 * the per-plugin "requested / delayed / world" maps each event used to keep.
 */
public enum SlotState {

    /** A definition has been chosen but nothing has been resolved yet. */
    PLANNED,

    /** {@code EventType.prepare} is in flight. */
    PREPARING,

    /** Fully resolved and waiting for its start time. */
    PREPARED,

    /** Running in the world. */
    RUNNING,

    /** Ran and ended normally. */
    FINISHED,

    /** Never ran: skipped, replaced, or its preparation failed. */
    CANCELLED;

    public boolean isTerminal() {
        return this == FINISHED || this == CANCELLED;
    }
}
