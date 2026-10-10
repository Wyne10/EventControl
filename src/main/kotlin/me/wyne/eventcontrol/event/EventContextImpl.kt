package me.wyne.eventcontrol.event

import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.event.EventContext
import me.wyne.eventcontrol.api.queue.QueueSlot
import me.wyne.eventcontrol.api.queue.QueueView
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

/**
 * What a running event is handed. Everything bound here is closed by the engine
 * when the event ends, which replaces the teardown each event plugin used to
 * hand-write.
 */
class EventContextImpl(
    private val queue: QueueView,
    private val slot: QueueSlot,
    private val location: Location?,
    private val endNow: () -> Unit
) : EventContext, AutoCloseable {

    private val bound = mutableListOf<AutoCloseable>()
    private val participantIds = linkedSetOf<UUID>()

    override fun getQueue(): QueueView = queue

    override fun getSlot(): QueueSlot = slot

    override fun <T : AutoCloseable> bind(closeable: T): T {
        bound.add(closeable)
        return closeable
    }

    override fun getParticipants(): Collection<Player> =
        participantIds.mapNotNull { EventControl.instance.server.getPlayer(it) }

    override fun addParticipant(player: Player) {
        participantIds.add(player.uniqueId)
    }

    override fun endNow() = endNow.invoke()

    /**
     * Closes in reverse order, so teardown undoes setup.
     */
    override fun close() {
        for (index in bound.indices.reversed()) {
            runCatching { bound[index].close() }
                .onFailure { EventControl.logger.error("Failed closing a resource bound to an event", it) }
        }
        bound.clear()
        participantIds.clear()
    }
}
