package me.wyne.eventcontrol.api.queue;

/** Why a slot holds the definition it holds. */
public enum SlotOrigin {

    /**
     * Chosen by the queue's configured source, whichever that is — the engine
     * does not distinguish between them, so a slot a player poll decided is
     * {@code SCHEDULED} like any other.
     */
    SCHEDULED,

    /** Asked for by an admin. */
    REQUESTED

}
