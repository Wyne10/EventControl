package me.wyne.eventcontrol.queue

import me.wyne.eventcontrol.api.spi.Durations
import me.wyne.eventcontrol.registry.isValidKey
import me.wyne.wutils.common.kotlin.config.deep
import me.wyne.wutils.config.configurables.attribute.GenericFactory
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import java.time.Duration

/**
 * A queue's configured recipe, read from `queue/<key>.yml`. Immutable; the live
 * timeline is an [EventQueue] built from it.
 */
data class QueueDefinition(
    val key: String,
    val worldName: String,
    val interval: Duration,
    val prepareLead: Duration,
    val lookahead: Int,
    val sourceKey: String,
    val sourceConfig: ConfigurationSection,
    val eventKeys: List<String>,
    val announcements: List<Announcement>,
    val autoStart: Boolean,
    val resumeInterrupted: Boolean
) {

    /** Null until the world is loaded, which on a fresh start is not immediate. */
    val world: World?
        get() = Bukkit.getWorld(worldName)

    object Factory : GenericFactory<QueueDefinition> {

        override fun create(key: String, config: ConfigurationSection): QueueDefinition {
            require(isValidKey(key)) {
                "Queue key '$key' must match [a-z0-9-]+ so it can be addressed in a placeholder"
            }
            val section = config.deep(key)
            val events = section.getStringList("events")
            require(events.isNotEmpty()) { "Queue '$key' lists no 'events'" }

            val lookahead = section.getInt("lookahead", 3)
            require(lookahead >= 1) { "Queue '$key' needs a 'lookahead' of at least 1" }

            val interval = Durations.read(section, "interval", Duration.ofMinutes(30))
            val prepareLead = Durations.read(section, "prepare-lead", Duration.ofMinutes(5))

            return QueueDefinition(
                key = key,
                worldName = section.getString("world", "world")!!,
                interval = interval,
                prepareLead = prepareLead,
                lookahead = lookahead,
                sourceKey = section.getString("source", "weighted-random")!!,
                sourceConfig = section,
                eventKeys = events,
                announcements = Announcement.readAll(section, "announce"),
                autoStart = section.getBoolean("auto-start", true),
                resumeInterrupted = section.getBoolean("resume-interrupted", false)
            )
        }
    }
}
