package me.wyne.eventcontrol.queue

import me.wyne.eventcontrol.api.queue.SlotOrigin

/**
 * One slot as written to disk. Only slots that have not run yet are kept — a
 * running event cannot survive a shutdown — so a restart resumes the timeline
 * instead of starting it over.
 */
data class PersistedSlot(
    val definitionKey: String? = null,
    val startAtEpochMilli: Long = 0L,
    val origin: SlotOrigin = SlotOrigin.SCHEDULED,
    val requester: String? = null
)
