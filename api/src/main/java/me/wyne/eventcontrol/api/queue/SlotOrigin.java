package me.wyne.eventcontrol.api.queue;

/** Why a slot holds the definition it holds. */
public enum SlotOrigin {

    /** Chosen by the queue's configured source. */
    SCHEDULED,

    /** Asked for by an admin. */
    REQUESTED,

    /** Won a player poll. */
    POLL,

    /** Inserted by another plugin through the API. */
    API
}
