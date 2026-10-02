package me.wyne.eventcontrol.event

import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.event.EventContext
import me.wyne.eventcontrol.api.queue.QueueSlot
import me.wyne.eventcontrol.api.queue.QueueView
import me.wyne.eventcontrol.api.text.AudienceContext
import org.bukkit.Location
import org.bukkit.World
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
) : EventContext, AudienceContext, AutoCloseable {

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

    override fun getWorld(): World = queue.world

    override fun getLocation(): Location? = location

    /** Closes in reverse order, so teardown undoes setup. */
    override fun close() {
        bound.asReversed().forEach { closeable ->
            runCatching { closeable.close() }
                .onFailure { EventControl.logger.error("Failed closing a resource bound to an event", it) }
        }
        bound.clear()
        participantIds.clear()
    }
}
