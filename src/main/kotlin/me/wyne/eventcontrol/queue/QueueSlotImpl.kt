package me.wyne.eventcontrol.queue

import me.wyne.eventcontrol.api.event.ActiveEvent
import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.api.event.PreparedEvent
import me.wyne.eventcontrol.api.queue.QueueSlot
import me.wyne.eventcontrol.api.queue.SlotOrigin
import me.wyne.eventcontrol.api.queue.SlotState
import java.time.Duration
import java.time.Instant

class QueueSlotImpl(
    private var origin: SlotOrigin,
    private var requester: String?,
    private var startAt: Instant
) : QueueSlot {

    private var state: SlotState = SlotState.PLANNED
    private var definition: EventDefinition? = null
    private var prepared: PreparedEvent? = null
    private var active: ActiveEvent? = null

    /** Set while preparation is in flight; polled by the type through its context. */
    var cancelled = false
        private set

    /** True while the source is still deciding, so the tick does not ask twice. */
    var pendingSource = false

    private val cancelCallbacks = mutableListOf<Runnable>()

    override fun getState(): SlotState = state
    override fun getOrigin(): SlotOrigin = origin
    override fun getRequester(): String? = requester
    override fun getDefinition(): EventDefinition? = definition
    override fun getStartAt(): Instant = startAt
    override fun getPrepared(): PreparedEvent? = prepared
    override fun getActive(): ActiveEvent? = active

    /**
     * The best current estimate of when this slot frees up. Before a definition
     * is chosen the duration is unknown, so the queue interval stands in.
     */
    fun estimatedEndAt(fallback: Duration): Instant =
        startAt.plus(definition?.duration ?: fallback)

    fun assign(definition: EventDefinition) {
        this.definition = definition
    }

    fun reschedule(startAt: Instant) {
        this.startAt = startAt
    }

    fun promoteOrigin(origin: SlotOrigin, requester: String?) {
        this.origin = origin
        this.requester = requester
    }

    fun markPreparing() {
        state = SlotState.PREPARING
    }

    fun markPrepared(prepared: PreparedEvent) {
        this.prepared = prepared
        state = SlotState.PREPARED
    }

    fun markRunning(active: ActiveEvent) {
        this.active = active
        state = SlotState.RUNNING
    }

    fun markFinished() {
        active = null
        state = SlotState.FINISHED
    }

    /**
     * Abandons the slot. Releases a preparation that already completed, and
     * signals one still in flight so it releases itself instead of leaking.
     */
    fun cancel() {
        if (state.isTerminal) return
        cancelled = true
        state = SlotState.CANCELLED
        cancelCallbacks.forEach(Runnable::run)
        cancelCallbacks.clear()
        prepared?.release()
        prepared = null
        active = null
    }

    fun onCancel(callback: Runnable) {
        if (cancelled) callback.run() else cancelCallbacks.add(callback)
    }
}
