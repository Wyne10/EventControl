package me.wyne.eventcontrol.queue

import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.api.event.PrepareContext
import me.wyne.eventcontrol.api.bukkit.QueueRunningChangeEvent
import me.wyne.eventcontrol.api.bukkit.QueueSlotAssignedEvent
import me.wyne.eventcontrol.api.bukkit.QueueSlotStateChangeEvent
import me.wyne.eventcontrol.api.event.PreparedEvent
import me.wyne.eventcontrol.api.queue.QueueSlot
import me.wyne.eventcontrol.api.queue.QueueSource
import me.wyne.eventcontrol.api.queue.QueueView
import me.wyne.eventcontrol.api.queue.SlotOrigin
import me.wyne.eventcontrol.api.queue.SlotState
import me.wyne.eventcontrol.event.EventContextImpl
import me.wyne.wutils.common.scheduler.Schedulers
import me.wyne.wutils.common.scheduler.Task
import me.wyne.wutils.common.terminable.Terminable
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.event.Event
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executor

/**
 * One queue's live timeline.
 *
 * The engine owns every scheduling state, so an event type owns none. A slot
 * being prepared, a slot waiting out its lead time and a slot running in the
 * world used to be three separate maps in every event plugin; here they are
 * three states of one list.
 *
 * `slots` holds only non-terminal slots: a slot is removed the moment it
 * finishes or is cancelled, so no loop here has to filter for that.
 *
 * Every assignment and every state change is reported as a Bukkit event (see
 * `api/bukkit`), which is how a plugin that owns none of this watches it.
 *
 * ## Why a tick rather than scheduled transitions
 *
 * The tick re-derives the timeline from current state once a second, so no
 * transition can be missed, double-fired, or left scheduled against a start
 * time that has since moved. Every invariant — `lookahead` slots planned, the
 * head prepared by `startAt - prepareLead`, a due slot launched, an over-run
 * slot ended — is re-established rather than carried in pending tasks.
 *
 * A task per transition would mean cancelling and rescheduling up to
 * `lookahead` tasks whenever anything shifts, and reload would have to track
 * them all. A second is finer than any of these transitions needs, and the work
 * is a handful of comparisons over at most `lookahead + 1` slots with no
 * allocation.
 */
class EventQueue(
    val definition: QueueDefinition,
    private val source: QueueSource,
    private val candidates: () -> List<EventDefinition>
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

    /** A copy: callers never receive the engine's live list. */
    override fun getUpcoming(): List<QueueSlot> = slots.toList()

    override fun getActiveSlot(): QueueSlot? = runningSlot()

    override fun getNextSlot(): QueueSlot? = slots.firstOrNull()

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
        call(QueueRunningChangeEvent(this, true))
    }

    fun stop() {
        if (!running) return
        running = false
        tickTask?.stop()
        tickTask = null
        endActive()
        nextAvailable = null
        EventControl.logger.info("Queue '{}' stopped", definition.key)
        call(QueueRunningChangeEvent(this, false))
    }

    /** Abandons the head slot, releasing whatever its preparation allocated. */
    fun skip(): EventDefinition? {
        val slot = slots.firstOrNull() ?: return null
        val skipped = slot.definition
        if (slot.state == SlotState.RUNNING) {
            endActive()
        } else {
            discard(slot)
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
        assign(slot, definition)

        // Anything already prepared was resolved against a start time the
        // owning plugin may already have shown, so it is discarded rather than
        // silently shifted.
        slots.filter { it.state == SlotState.PREPARED }.forEach(::discard)

        val insertAt = slots.indexOfFirst { it.state == SlotState.PLANNED || it.state == SlotState.PREPARING }
        if (insertAt < 0) slots.add(slot) else slots.add(insertAt, slot)

        rescheduleFrom(Instant.now())
        return slot
    }

    //endregion

    //region The tick

    private fun tick() {
        if (!running) return
        val now = Instant.now()

        endFinished(now)
        plan(now)
        rescheduleFrom(now)
        prepare(now)
        launch(now)
    }

    /**
     * Abandons a slot and drops it, releasing whatever its preparation had
     * already allocated. Keeping `slots` free of terminal entries is what lets
     * every loop here iterate it directly.
     */
    private fun discard(slot: QueueSlotImpl) {
        transition(slot) { slot.cancel() }
        slots.remove(slot)
    }

    private fun endFinished(now: Instant) {
        val slot = runningSlot() ?: return
        val prepared = slot.prepared ?: return
        if (now >= prepared.endAt) endActive()
    }

    /**
     * Keeps `lookahead` slots planned, asking the source to fill the empty ones.
     * A new slot is appended with a placeholder time; `rescheduleFrom` runs
     * later in the same tick and assigns the real one before anything reads it.
     */
    private fun plan(now: Instant) {
        while (slots.size < definition.lookahead) {
            slots.add(QueueSlotImpl(SlotOrigin.SCHEDULED, null, now))
        }
        slots.forEach { slot ->
            if (slot.state == SlotState.PLANNED && slot.definition == null) ask(slot)
        }
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
                        else -> assign(slot, chosen)
                    }
                }
            }
    }

    /**
     * Lays the planned slots out after whatever is already fixed.
     *
     * Only `PLANNED` slots move. A prepared slot's start time is already
     * settled and may already have been shown to players, so shifting it would
     * make that a lie; a running one is already out in the world.
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
        if (runningSlot() == null && cursor.isBefore(earliest)) {
            cursor = earliest
            nextAvailable = cursor
        }

        slots.forEach { slot ->
            if (slot.state == SlotState.PLANNED && slot.startAt != cursor)
                slot.reschedule(cursor)
            val cooldown = slot.definition?.cooldown ?: Duration.ZERO
            cursor = maxOf(slot.estimatedEndAt(definition.interval), now)
                .plus(maxOf(definition.interval, cooldown))
        }
    }

    /**
     * Resolves the head slot ahead of time. This is what makes an event
     * predictable: by the time anything is shown to a player, the location and
     * every other fact already exist.
     */
    private fun prepare(now: Instant) {
        val slot = slots.firstOrNull() ?: return
        if (slot.state != SlotState.PLANNED) return
        val definition = slot.definition ?: return
        if (now.isBefore(slot.startAt.minus(this.definition.prepareLead))) return

        transition(slot) { slot.markPreparing() }
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
        transition(slot) { slot.markPrepared(prepared) }
        EventControl.logger.debug(
            "Prepared '{}' for queue '{}', starting at {}", definition.key, this.definition.key, slot.startAt
        )
    }

    private fun failPreparation(slot: QueueSlotImpl, definition: EventDefinition, failure: Throwable?) {
        EventControl.logger.error(
            "Failed preparing '{}' for queue '{}', replanning the slot", definition.key, this.definition.key, failure
        )
        discard(slot)
    }

    private fun launch(now: Instant) {
        if (runningSlot() != null) return
        val slot = slots.firstOrNull() ?: return
        if (slot.state != SlotState.PREPARED) return
        val prepared = slot.prepared ?: return
        if (now.isBefore(slot.startAt)) return

        val context = EventContextImpl(this, slot, prepared.location) { endActive() }
        runCatching { prepared.start(context) }
            .onSuccess { active ->
                transition(slot) { slot.markRunning(active) }
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
                discard(slot)
            }
    }

    private fun endActive() {
        val slot = runningSlot() ?: return
        val active = slot.active

        runCatching { active?.onEnd() }
            .onFailure { EventControl.logger.error("An event failed while ending", it) }

        activeContext?.close()
        activeContext = null
        transition(slot) { slot.markFinished() }
        slots.remove(slot)

        val now = Instant.now()
        nextAvailable = now.plus(definition.interval)
        rescheduleFrom(now)
    }

    private fun runningSlot(): QueueSlotImpl? = slots.firstOrNull { it.state == SlotState.RUNNING }

    private fun assign(slot: QueueSlotImpl, definition: EventDefinition) {
        slot.assign(definition)
        call(QueueSlotAssignedEvent(this, slot, definition))
    }

    /**
     * Applies a state change and reports it, so no transition can be made
     * without the corresponding event going out. Nothing is fired when the
     * change was a no-op — cancelling an already terminal slot, say.
     */
    private inline fun transition(slot: QueueSlotImpl, change: () -> Unit) {
        val previous = slot.state
        change()
        if (slot.state != previous)
            call(QueueSlotStateChangeEvent(this, slot, previous, slot.state))
    }

    /**
     * Bukkit logs and swallows a listener's exception per listener, so a
     * misbehaving one cannot break the tick. It would throw if called off the
     * main thread, which is deliberate: every call site here is on it.
     */
    private fun call(event: Event) {
        Bukkit.getPluginManager().callEvent(event)
    }

    //endregion

    /** The timeline as it stands, for writing to disk so a restart resumes it. */
    fun persistableSlots(): List<QueueSlotImpl> = slots.toList()

    /**
     * Resumes a saved timeline. The head's saved start time becomes the anchor,
     * so an event already scheduled for a given time keeps it instead of being
     * pulled earlier by the restart.
     */
    fun restore(restored: List<QueueSlotImpl>) {
        slots.clear()
        slots.addAll(restored)
        nextAvailable = restored.firstOrNull()?.startAt
        // Restored slots arrive already assigned, so report them too: a listener
        // can then build its own state from these events alone instead of also
        // having to read the timeline once at startup.
        slots.forEach { slot ->
            slot.definition?.let { call(QueueSlotAssignedEvent(this, slot, it)) }
        }
    }

    override fun close() {
        stop()
        slots.toList().forEach { slot -> transition(slot) { slot.cancel() } }
        slots.clear()
    }

    private companion object {
        /** One second: the engine only ever compares instants, never counts ticks. */
        const val TICK_INTERVAL_TICKS = 20L
    }
}

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
