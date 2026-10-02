package me.wyne.eventcontrol.queue

import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.api.event.PrepareContext
import me.wyne.eventcontrol.api.event.PreparedEvent
import me.wyne.eventcontrol.api.queue.QueueSlot
import me.wyne.eventcontrol.api.queue.QueueSource
import me.wyne.eventcontrol.api.queue.QueueView
import me.wyne.eventcontrol.api.queue.SlotOrigin
import me.wyne.eventcontrol.api.queue.SlotState
import me.wyne.eventcontrol.event.Announcer
import me.wyne.eventcontrol.event.EventContextImpl
import me.wyne.wutils.common.scheduler.Schedulers
import me.wyne.wutils.common.scheduler.Task
import me.wyne.wutils.common.terminable.Terminable
import org.bukkit.World
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executor

/**
 * One queue's live timeline.
 *
 * The engine owns every scheduling state, so an event type owns none. A slot
 * being prepared, a slot waiting out its lead time and a slot running in the
 * world used to be three separate maps in every event plugin; here they are
 * three states of one list, advanced by a single tick.
 *
 * The invariant the tick maintains: `lookahead` slots are always planned, and
 * the head is always prepared by `startAt - prepareLead`.
 */
class EventQueue(
    val definition: QueueDefinition,
    private val source: QueueSource,
    private val candidates: () -> List<EventDefinition>,
    private val announcer: Announcer
) : QueueView, Terminable {

    private val slots = mutableListOf<QueueSlotImpl>()
    private var tickTask: Task? = null
    private var running = false
    private var activeContext: EventContextImpl? = null

    /**
     * When the queue is next free to run something. Moved only at discrete
     * moments — the queue starting, and an event ending — so a planned start
     * time stays put instead of being recomputed against a moving `now`.
     */
    private var nextAvailable: Instant? = null

    override fun getKey(): String = definition.key

    override fun getWorld(): World = requireNotNull(definition.world) {
        "Queue '${definition.key}' targets world '${definition.worldName}', which is not loaded"
    }

    override fun getInterval(): Duration = definition.interval

    override fun getPrepareLead(): Duration = definition.prepareLead

    override fun getLookahead(): Int = definition.lookahead

    override fun isRunning(): Boolean = running

    override fun getUpcoming(): List<QueueSlot> = slots.filterNot { it.state.isTerminal }

    override fun getActiveSlot(): QueueSlot? = slots.firstOrNull { it.state == SlotState.RUNNING }

    override fun getNextSlot(): QueueSlot? = upcoming.firstOrNull()

    override fun getCandidates(): List<EventDefinition> = candidates()

    //region Control

    fun start() {
        if (running) return
        if (definition.world == null) {
            EventControl.logger.warn(
                "Not starting queue '{}': world '{}' is not loaded", definition.key, definition.worldName
            )
            return
        }
        running = true
        // A restore may already have anchored the timeline; only a queue with
        // nothing resumed starts counting from now.
        if (nextAvailable == null) nextAvailable = Instant.now().plus(definition.prepareLead)
        // load() restarts this task, so it must always stop the previous one
        // first or a reload doubles the tick.
        tickTask?.stop()
        tickTask = Schedulers.sync().runRepeating({ _ -> tick() }, 0L, TICK_INTERVAL_TICKS)
        EventControl.logger.info("Queue '{}' started", definition.key)
    }

    fun stop() {
        if (!running) return
        running = false
        tickTask?.stop()
        tickTask = null
        endActive()
        nextAvailable = null
        EventControl.logger.info("Queue '{}' stopped", definition.key)
    }

    /** Abandons the head slot, releasing whatever its preparation allocated. */
    fun skip(): EventDefinition? {
        val slot = slots.firstOrNull { !it.state.isTerminal } ?: return null
        val skipped = slot.definition
        if (slot.state == SlotState.RUNNING) {
            endActive()
        } else {
            slot.cancel()
            prune()
            val now = Instant.now()
            nextAvailable = now.plus(definition.prepareLead)
            rescheduleFrom(now)
        }
        return skipped
    }

    /** Ends the running event early, without touching the rest of the timeline. */
    fun stopCurrent(): Boolean {
        if (activeSlot == null) return false
        endActive()
        return true
    }

    /**
     * Puts [definition] at the front of the timeline. An explicit request
     * outranks whatever the configured source would have chosen.
     */
    fun request(definition: EventDefinition, requester: String?): QueueSlot {
        val slot = QueueSlotImpl(SlotOrigin.REQUESTED, requester, Instant.now())
        slot.assign(definition)

        val insertAt = slots.indexOfFirst { it.state == SlotState.PLANNED || it.state == SlotState.PREPARING }
        // Anything already prepared has been announced with a start time, so it
        // is cancelled rather than silently shifted.
        if (insertAt < 0) slots.add(slot) else {
            slots.filter { it.state == SlotState.PREPARED }.forEach(QueueSlotImpl::cancel)
            slots.add(insertAt, slot)
        }
        prune()
        rescheduleFrom(Instant.now())
        return slot
    }

    //endregion

    //region The tick

    private fun tick() {
        if (!running) return
        val now = Instant.now()

        prune()
        endFinished(now)
        plan(now)
        rescheduleFrom(now)
        prepare(now)
        // Launch before announcing: a message anchored at or after the start
        // must not go out when starting the event actually failed.
        launch(now)
        announce(now)
    }

    /** Drops terminal slots so `upcoming` only ever shows what is still ahead. */
    private fun prune() {
        slots.removeAll { it.state.isTerminal }
    }

    private fun endFinished(now: Instant) {
        val slot = slots.firstOrNull { it.state == SlotState.RUNNING } ?: return
        val prepared = slot.prepared ?: return
        if (now >= prepared.endAt) endActive()
    }

    /**
     * Keeps `lookahead` slots planned, asking the source to fill the empty ones.
     * A new slot is appended with a placeholder time; `rescheduleFrom` runs
     * later in the same tick and assigns the real one before anything reads it.
     */
    private fun plan(now: Instant) {
        while (slots.count { !it.state.isTerminal } < definition.lookahead) {
            slots.add(QueueSlotImpl(SlotOrigin.SCHEDULED, null, now))
        }
        slots.filter { it.state == SlotState.PLANNED && it.definition == null }
            .forEach { slot -> ask(slot) }
    }

    private fun ask(slot: QueueSlotImpl) {
        if (slot.pendingSource) return
        slot.pendingSource = true
        runCatching { source.nextFor(this, slot) }
            .onFailure {
                slot.pendingSource = false
                EventControl.logger.error("Source '{}' failed for queue '{}'", definition.sourceKey, definition.key, it)
            }
            .getOrNull()
            ?.whenComplete { chosen, failure ->
                Schedulers.sync().run {
                    slot.pendingSource = false
                    when {
                        slot.state.isTerminal -> Unit
                        failure != null -> EventControl.logger.error(
                            "Source '{}' failed for queue '{}'", definition.sourceKey, definition.key, failure
                        )
                        chosen == null -> EventControl.logger.debug(
                            "Source '{}' had nothing to run for queue '{}'", definition.sourceKey, definition.key
                        )
                        else -> slot.assign(chosen)
                    }
                }
            }
    }

    /**
     * Lays the planned slots out after whatever is already fixed.
     *
     * Only `PLANNED` slots move. A prepared slot has been announced with a start
     * time, so shifting it would make the announcement a lie; a running one is
     * already out in the world.
     *
     * A planned slot is set to the cursor exactly, not merely pushed later: a
     * slot is first planned before its source has chosen a definition, so its
     * length is a guess until then, and the timeline has to tighten up once the
     * real duration is known instead of keeping the guess forever.
     */
    private fun rescheduleFrom(now: Instant) {
        val earliest = now.plus(definition.prepareLead)
        var cursor = nextAvailable ?: earliest

        // Nothing can start sooner than it can be prepared. Writing the clamp
        // back to the anchor keeps it from being reapplied against a later
        // `now` on every tick, which would push the head away forever.
        if (activeSlot == null && cursor.isBefore(earliest)) {
            cursor = earliest
            nextAvailable = cursor
        }

        slots.filterNot { it.state.isTerminal }.forEach { slot ->
            if (slot.state == SlotState.PLANNED && slot.startAt != cursor)
                slot.reschedule(cursor)
            val cooldown = slot.definition?.cooldown ?: Duration.ZERO
            cursor = maxOf(slot.estimatedEndAt(definition.interval), now)
                .plus(maxOf(definition.interval, cooldown))
        }
    }

    /**
     * Resolves the head slot ahead of time. This is what makes an event
     * predictable: by the time anything is announced or shown, the location and
     * every other fact already exist.
     */
    private fun prepare(now: Instant) {
        val slot = slots.firstOrNull { !it.state.isTerminal } ?: return
        if (slot.state != SlotState.PLANNED) return
        val definition = slot.definition ?: return
        if (now.isBefore(slot.startAt.minus(this.definition.prepareLead))) return

        slot.markPreparing()
        val context = SlotPrepareContext(this, slot, world, slot.startAt)

        runCatching { definition.type.prepare(definition, context) }
            .onFailure { failPreparation(slot, definition, it) }
            .getOrNull()
            ?.whenComplete { prepared, failure ->
                Schedulers.sync().run { settle(slot, definition, prepared, failure) }
            }
    }

    private fun settle(
        slot: QueueSlotImpl,
        definition: EventDefinition,
        prepared: PreparedEvent?,
        failure: Throwable?
    ) {
        if (slot.state.isTerminal) {
            // The slot was abandoned while preparation was in flight.
            prepared?.release()
            return
        }
        if (failure != null || prepared == null) {
            failPreparation(slot, definition, failure)
            return
        }
        slot.markPrepared(prepared)
        EventControl.logger.debug(
            "Prepared '{}' for queue '{}', starting at {}", definition.key, this.definition.key, slot.startAt
        )
    }

    private fun failPreparation(slot: QueueSlotImpl, definition: EventDefinition, failure: Throwable?) {
        EventControl.logger.error(
            "Failed preparing '{}' for queue '{}', replanning the slot", definition.key, this.definition.key, failure
        )
        slot.cancel()
        prune()
    }

    private fun announce(now: Instant) {
        slots.filterNot { it.state.isTerminal }.forEach { slot ->
            val prepared = slot.prepared ?: return@forEach
            definition.announcements
                .filter { it.trigger == Announcement.Trigger.START }
                .forEach { announcement ->
                    val due = prepared.startAt.plusMillis(announcement.offsetMillis)
                    if (now < due) return@forEach
                    // A warning ahead of the event is true as soon as the event
                    // is prepared. Anything from the start onwards is only true
                    // once it is actually running.
                    if (announcement.offsetMillis >= 0 && slot.state != SlotState.RUNNING) return@forEach
                    if (slot.sentAnnouncements.add(announcement.identity()))
                        dispatch(announcement, slot, prepared)
                }
        }
    }

    private fun launch(now: Instant) {
        if (activeSlot != null) return
        val slot = slots.firstOrNull { !it.state.isTerminal } ?: return
        if (slot.state != SlotState.PREPARED) return
        val prepared = slot.prepared ?: return
        if (now.isBefore(slot.startAt)) return

        val context = EventContextImpl(this, slot, prepared.location) { endActive() }
        runCatching { prepared.start(context) }
            .onSuccess { active ->
                slot.markRunning(active)
                activeContext = context
                EventControl.logger.info(
                    "Queue '{}' started event '{}'", definition.key, prepared.definition.key
                )
            }
            .onFailure {
                EventControl.logger.error(
                    "Failed starting '{}' on queue '{}'", prepared.definition.key, definition.key, it
                )
                context.close()
                slot.cancel()
                prune()
            }
    }

    private fun endActive() {
        val slot = slots.firstOrNull { it.state == SlotState.RUNNING } ?: return
        val prepared = slot.prepared
        val active = slot.active

        runCatching { active?.onEnd() }
            .onFailure { EventControl.logger.error("An event failed while ending", it) }

        if (prepared != null) {
            definition.announcements
                .filter { it.trigger == Announcement.Trigger.END }
                .forEach { announcement ->
                    if (slot.sentAnnouncements.add(announcement.identity())) dispatch(announcement, slot, prepared)
                }
        }

        activeContext?.close()
        activeContext = null
        slot.markFinished()
        prune()

        val now = Instant.now()
        nextAvailable = now.plus(definition.interval)
        rescheduleFrom(now)
    }

    private fun dispatch(announcement: Announcement, slot: QueueSlotImpl, prepared: PreparedEvent) {
        val context = activeContext ?: SimpleAudienceContext(world, prepared.location, emptyList())
        announcer.announce(announcement.key, announcement.audience, this, prepared, context)
    }

    //endregion

    /** Non-terminal slots worth writing to disk, so a restart resumes the timeline. */
    fun persistableSlots(): List<QueueSlotImpl> = slots.filterNot { it.state.isTerminal }

    /**
     * Resumes a saved timeline. The head's saved start time becomes the anchor,
     * so an event an admin already announced for a given time keeps it instead
     * of being pulled earlier by the restart.
     */
    fun restore(restored: List<QueueSlotImpl>) {
        slots.clear()
        slots.addAll(restored)
        nextAvailable = restored.firstOrNull()?.startAt
    }

    override fun close() {
        stop()
        slots.forEach(QueueSlotImpl::cancel)
        slots.clear()
    }

    private companion object {
        /** One second: the engine only ever compares instants, never counts ticks. */
        const val TICK_INTERVAL_TICKS = 20L
    }
}

private fun Announcement.identity() = "${trigger.name}:$offsetMillis:$key"

/** The [PrepareContext] handed to one slot's preparation. */
private class SlotPrepareContext(
    private val queue: EventQueue,
    private val slot: QueueSlotImpl,
    private val world: World,
    private val startAt: Instant
) : PrepareContext {

    override fun getQueue(): QueueView = queue
    override fun getSlot(): QueueSlot = slot
    override fun getWorld(): World = world
    override fun getStartAt(): Instant = startAt

    override fun getRemainingLeadMillis(): Long =
        Duration.between(Instant.now(), startAt).toMillis().coerceAtLeast(0L)

    override fun getAsyncExecutor(): Executor = Schedulers.async()

    override fun isCancelled(): Boolean = slot.cancelled

    override fun onCancel(callback: Runnable) = slot.onCancel(callback)
}
