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
import me.wyne.wutils.i18n.kotlin.placeholderComponent
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
 *
 * Durations are raw milliseconds. Formatting belongs to the DateTimeFormat
 * expansion, which the server owner points at these values:
 * `%dtf_duration_{eventcontrol_main_next_starts-in}ms_mm'm 'ss's'%`.
 *
 * `display` is the one field that comes out of a language file rather than the
 * timeline, so it carries i18n's serializer suffixes — `display-legacy`,
 * `display-mm`, `display-plain` and the rest. Whoever reads the placeholder
 * picks the markup; this expansion does not choose one for them.
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

    override fun onRequest(viewer: OfflinePlayer?, params: String): String? {
        val args = Args(params, "_")
        if (args.size() < 2) return null

        val queue = queues[args.get(0)] ?: return null
        val field = args.get(1)

        queueField(queue, field)?.let { return it }

        val slot = slotFor(queue, field) ?: return null
        val slotField = if (args.size() >= 3) args.get(2) else "name"
        return slotField(slot, slotField, viewer)
    }

    private fun queueField(queue: QueueView, field: String): String? = when (field) {
        "running" -> queue.isRunning.toString()
        "upcoming" -> queue.upcoming.size.toString()
        "lookahead" -> queue.lookahead.toString()
        "interval" -> queue.interval.toMillis().toString()
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

    private fun slotField(slot: QueueSlot, field: String, viewer: OfflinePlayer?): String? {
        val definition = slot.definition
        val prepared = slot.prepared

        // The resolved name, not the key: every other field here is a value.
        // `style` answers `display` and each of its serializer variants, and null
        // for anything else, so a mistyped suffix stays unresolved rather than
        // quietly rendering as the default.
        if (field.startsWith(DISPLAY_FIELD)) {
            val named = definition ?: return ""
            return viewer.placeholderComponent(named.displayKey).style(DISPLAY_FIELD, field)
        }

        return when (field) {
            "name", "key" -> definition?.key ?: ""
            "state" -> slot.state.name.lowercase()
            "origin" -> slot.origin.name.lowercase()
            "requester" -> slot.requester ?: ""
            "prepared" -> (prepared != null).toString()
            "in", "starts-in" -> millisUntil(slot.startAt)
            "ends-in" -> prepared?.let { millisUntil(it.endAt) } ?: ""
            "duration" -> definition?.duration?.toMillis()?.toString() ?: ""
            "x" -> prepared?.location?.blockX?.toString() ?: ""
            "y" -> prepared?.location?.blockY?.toString() ?: ""
            "z" -> prepared?.location?.blockZ?.toString() ?: ""
            "position" -> prepared?.location
                ?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: ""

            "world" -> prepared?.location?.world?.name ?: ""
            else -> null
        }
    }

    private fun millisUntil(target: Instant): String =
        Duration.between(Instant.now(), target).toMillis().coerceAtLeast(0L).toString()

    companion object {
        /** i18n appends its serializer suffixes to this, so it is matched as a prefix. */
        private const val DISPLAY_FIELD = "display"

        lateinit var instance: QueuePlaceholders
            private set
    }
}
