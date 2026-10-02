package me.wyne.eventcontrol.event

import com.google.inject.Inject
import com.google.inject.Singleton
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.event.PreparedEvent
import me.wyne.eventcontrol.api.queue.QueueView
import me.wyne.eventcontrol.api.text.Audience
import me.wyne.eventcontrol.api.text.AudienceContext
import me.wyne.eventcontrol.registry.Registries
import me.wyne.wutils.i18n.kotlin.placeholderComponent
import org.bukkit.Location
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant

/**
 * Sends one announcement to one audience, resolving the message against the
 * placeholders the event contributes.
 *
 * There is no notification registry: the text lives in the language file of
 * whichever plugin owns the key, so a provider ships its own wording.
 */
@Singleton
class Announcer @Inject constructor(private val registries: Registries) {

    fun announce(
        key: String,
        audience: Audience,
        queue: QueueView,
        event: PreparedEvent,
        context: AudienceContext
    ) {
        val receivers = runCatching { audience.resolve(context) }
            .onFailure { EventControl.logger.error("Failed resolving the audience for '{}'", key, it) }
            .getOrNull() ?: return
        if (receivers.isEmpty()) {
            EventControl.logger.debug("Announcement '{}' resolved to no receivers", key)
            return
        }

        receivers.forEach { player -> send(key, queue, event, player) }
    }

    private fun send(key: String, queue: QueueView, event: PreparedEvent, player: Player) {
        val sink = PlaceholderSinkImpl()
        contributeGeneric(sink, queue, event, player)
        registries.placeholderContributors.forEach { contributor ->
            runCatching { contributor.contribute(sink, event, player) }
                .onFailure { EventControl.logger.error("A placeholder contributor failed for '{}'", key, it) }
        }
        runCatching { event.contributePlaceholders(sink) }
            .onFailure {
                EventControl.logger.error(
                    "Event '{}' failed contributing its placeholders", event.definition.key, it
                )
            }

        runCatching {
            player.placeholderComponent(key, *sink.textReplacements())
                .replace(*sink.componentReplacements())
                .sendMessage(player)
        }.onFailure { EventControl.logger.error("Failed sending announcement '{}'", key, it) }
    }

    /**
     * The values every event has. An event only has to contribute what it alone
     * knows, which is the whole point of the sink.
     */
    private fun contributeGeneric(
        sink: PlaceholderSinkImpl,
        queue: QueueView,
        event: PreparedEvent,
        viewer: Player
    ) {
        val definition = event.definition
        sink.text("queue", queue.key)
        sink.text("event", definition.key)
        sink.text("world", queue.world.name)
        sink.text("duration", format(definition.duration))
        sink.text("starts-in", format(between(Instant.now(), event.startAt)))
        sink.text("ends-in", format(between(Instant.now(), event.endAt)))

        sink.component("event-name", viewer.placeholderComponent(definition.displayKey).miniMessage())

        event.location?.let { location ->
            sink.text("x", location.blockX.toString())
            sink.text("y", location.blockY.toString())
            sink.text("z", location.blockZ.toString())
            sink.text("position", position(location))
            sink.text("distance", viewer.location.let {
                if (it.world == location.world) it.distance(location).toLong().toString() else "?"
            })
        }
    }

    private fun position(location: Location) =
        "${location.blockX}, ${location.blockY}, ${location.blockZ}"

    private fun between(from: Instant, to: Instant): Duration =
        Duration.between(from, to).let { if (it.isNegative) Duration.ZERO else it }

    private fun format(duration: Duration): String {
        val seconds = duration.seconds
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainder = seconds % 60
        return when {
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, remainder)
            else -> "%d:%02d".format(minutes, remainder)
        }
    }
}
