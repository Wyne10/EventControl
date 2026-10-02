package me.wyne.eventcontrol.queue

import me.wyne.eventcontrol.api.spi.Durations
import me.wyne.eventcontrol.api.text.Audience
import me.wyne.eventcontrol.api.text.AudienceContext
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.MemoryConfiguration
import org.bukkit.entity.Player

/**
 * One scheduled message. Because the schedule is expressed against a start time
 * the engine knows in advance, an early warning and the start message are the
 * same mechanism rather than two separately triggered broadcasts.
 */
data class Announcement(
    val trigger: Trigger,
    val offsetMillis: Long,
    val key: String,
    val audience: Audience
) {

    enum class Trigger { START, END }

    companion object Factory {

        private const val END_ANCHOR = "end"

        /**
         * Reads one entry. `at` carries both the anchor and the offset:
         *
         *  - `at: -30s` — thirty seconds before the event starts
         *  - `at: 0` — as it starts
         *  - `at: end` — as it ends
         *  - `at: end-10s` — ten seconds before it ends
         *
         * There is deliberately no `on:` key. SnakeYAML follows YAML 1.1, which
         * resolves a bare `on` to the boolean `true` even as a mapping key, so
         * an `on: end` entry silently parses as something else entirely.
         */
        fun read(config: ConfigurationSection): Announcement {
            val key = requireNotNull(config.getString("key")) { "An announcement needs a 'key'" }
            val audience = Audiences.parse(config.getString("audience", "world")!!)
            val at = config.getString("at", "0")!!.trim().lowercase()

            if (at.startsWith(END_ANCHOR)) {
                val remainder = at.removePrefix(END_ANCHOR)
                return Announcement(Trigger.END, parseOffset(remainder.ifEmpty { "0" }), key, audience)
            }
            return Announcement(Trigger.START, parseOffset(at), key, audience)
        }

        /** Reads either a list of announcement maps or a section of named entries. */
        fun readAll(config: ConfigurationSection, path: String): List<Announcement> {
            config.getConfigurationSection(path)?.let { section ->
                return section.getKeys(false).mapNotNull { section.getConfigurationSection(it) }.map(::read)
            }
            return config.getMapList(path).map { entry ->
                val holder = MemoryConfiguration()
                entry.forEach { (key, value) ->
                    // YAML 1.1 turns a bare `on` key into the boolean true, so an
                    // entry written with `on:` arrives unrecognisable. Say so.
                    require(key.toString() != "true") {
                        "Use 'at: end' instead of 'on: end' in an announcement: YAML reads a bare " +
                            "'on' key as a boolean, so the entry never parses"
                    }
                    holder.set(key.toString(), value)
                }
                read(holder)
            }
        }

        private fun parseOffset(value: String): Long {
            val trimmed = value.trim()
            if (trimmed.isEmpty() || trimmed == "0") return 0L
            val negative = trimmed.startsWith("-")
            val magnitude = Durations.parse(trimmed.removePrefix("-").removePrefix("+")).toMillis()
            return if (negative) -magnitude else magnitude
        }
    }
}

/** Parses the `audience:` vocabulary into an [Audience]. */
object Audiences {

    fun parse(value: String): Audience {
        val trimmed = value.trim()
        val separator = trimmed.indexOf(':')
        val name = if (separator < 0) trimmed else trimmed.substring(0, separator)
        val argument = if (separator < 0) null else trimmed.substring(separator + 1).trim()

        return when (name.lowercase()) {
            "all" -> Audience { Bukkit.getOnlinePlayers().toList() }
            "world" -> Audience { context -> context.world.players }
            "participants" -> Audience { context -> context.participants.toList() }
            "radius" -> radius(
                requireNotNull(argument?.toDoubleOrNull()) { "audience 'radius' needs a distance, e.g. radius:120" }
            )
            "permission" -> permission(
                requireNotNull(argument?.takeIf { it.isNotEmpty() }) {
                    "audience 'permission' needs a node, e.g. permission:eventcontrol.event"
                }
            )
            else -> throw IllegalArgumentException(
                "Unknown audience '$value', expected all, world, participants, radius:<n> or permission:<node>"
            )
        }
    }

    private fun radius(distance: Double): Audience {
        val squared = distance * distance
        return Audience { context ->
            val location = context.location ?: return@Audience context.world.players
            context.world.players.filter { it.location.distanceSquared(location) <= squared }
        }
    }

    private fun permission(node: String): Audience = Audience { context ->
        context.world.players.filter { it.hasPermission(node) }
    }
}

/** The facts an [Audience] resolves against, before or during an event. */
class SimpleAudienceContext(
    private val world: org.bukkit.World,
    private val location: org.bukkit.Location?,
    private val participants: Collection<Player>
) : AudienceContext {
    override fun getWorld() = world
    override fun getLocation() = location
    override fun getParticipants() = participants
}
