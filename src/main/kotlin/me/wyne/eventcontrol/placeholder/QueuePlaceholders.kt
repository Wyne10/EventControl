package me.wyne.eventcontrol.placeholder

import com.google.inject.Inject
import com.google.inject.Singleton
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.queue.QueueSlot
import me.wyne.eventcontrol.api.queue.QueueView
import me.wyne.eventcontrol.api.queue.SlotState
import me.wyne.eventcontrol.queue.QueueService
import me.wyne.wutils.common.Args
import org.bukkit.OfflinePlayer
import java.time.Duration
import java.time.Instant

/**
 * `%eventcontrol_<queue>_<field>%`, plus `%eventcontrol_<queue>_next<n>_<field>%`
 * for a slot further down the timeline.
 *
 * Every value is read from the in-memory timeline, never from disk, because
 * placeholders resolve on the main thread once per player per frame. The
 * upcoming fields work at all only because slots are prepared ahead of time —
 * there is nothing here to smuggle through another plugin's namespace.
 */
@Singleton
class QueuePlaceholders @Inject constructor(private val queues: QueueService) : PlaceholderExpansion() {

    init {
        register()
        instance = this
    }

    override fun getIdentifier() = "eventcontrol"

    override fun getAuthor() = EventControl.instance.description.authors.joinToString()

    override fun getVersion() = EventControl.instance.description.version

    override fun persist() = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val args = Args(params, "_")
        if (args.size() < 2) return null

        val queue = queues[args.get(0)] ?: return null
        val field = args.get(1)

        queueField(queue, field)?.let { return it }

        val slot = slotFor(queue, field) ?: return null
        val slotField = if (args.size() >= 3) args.get(2) else "name"
        return slotField(slot, slotField)
    }

    private fun queueField(queue: QueueView, field: String): String? = when (field) {
        "running" -> queue.isRunning.toString()
        "upcoming" -> queue.upcoming.size.toString()
        "lookahead" -> queue.lookahead.toString()
        "world" -> queue.world.name
        "interval" -> format(queue.interval)
        else -> null
    }

    /**
     * `active` is the running slot; `next` the one due next; `next2`, `next3`
     * and so on walk further down the prepared lookahead.
     */
    private fun slotFor(queue: QueueView, field: String): QueueSlot? = when {
        field == "active" -> queue.activeSlot
        field == "next" -> pending(queue).firstOrNull()
        field.startsWith("next") -> field.removePrefix("next").toIntOrNull()
            ?.takeIf { it >= 1 }
            ?.let { pending(queue).getOrNull(it - 1) }
        else -> null
    }

    private fun pending(queue: QueueView) = queue.upcoming.filter { it.state != SlotState.RUNNING }

    private fun slotField(slot: QueueSlot, field: String): String? {
        val definition = slot.definition
        val prepared = slot.prepared
        return when (field) {
            "name", "key" -> definition?.key ?: ""
            "display" -> definition?.displayKey ?: ""
            "state" -> slot.state.name.lowercase()
            "origin" -> slot.origin.name.lowercase()
            "requester" -> slot.requester ?: ""
            "prepared" -> (prepared != null).toString()
            "in", "starts-in" -> format(until(slot.startAt))
            "ends-in" -> prepared?.let { format(until(it.endAt)) } ?: ""
            "duration" -> definition?.let { format(it.duration) } ?: ""
            "x" -> prepared?.location?.blockX?.toString() ?: ""
            "y" -> prepared?.location?.blockY?.toString() ?: ""
            "z" -> prepared?.location?.blockZ?.toString() ?: ""
            "position" -> prepared?.location
                ?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: ""
            else -> null
        }
    }

    private fun until(target: Instant) =
        Duration.between(Instant.now(), target).let { if (it.isNegative) Duration.ZERO else it }

    private fun format(duration: Duration): String {
        val seconds = duration.seconds
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainder = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
        else "%d:%02d".format(minutes, remainder)
    }

    companion object {
        lateinit var instance: QueuePlaceholders
            private set
    }
}
