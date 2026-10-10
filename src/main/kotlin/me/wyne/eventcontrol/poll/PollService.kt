package me.wyne.eventcontrol.poll

import com.google.inject.Inject
import com.google.inject.Singleton
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.registry.Registries
import me.wyne.wutils.i18n.kotlin.placeholderComponent
import me.wyne.wutils.i18n.kotlin.replace
import me.wyne.wutils.i18n.kotlin.replaceComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Holds the open ballots and tells players about them. */
@Singleton
class PollService @Inject constructor(registries: Registries) {

    private val open = linkedMapOf<String, Poll>()

    init {
        registries.queueSources().register("poll") { key, config ->
            PollSource.create(this, key, config)
        }
    }

    val all: Collection<Poll>
        get() = open.values.toList()

    operator fun get(queueKey: String): Poll? = open[queueKey]?.takeIf { it.isOpen }

    fun open(poll: Poll) {
        open[poll.queueKey]?.abandon()
        open[poll.queueKey] = poll
        EventControl.logger.info(
            "Opened a poll on queue '{}' with {} candidate(s)", poll.queueKey, poll.candidates.size
        )
        announceOpened(poll)
    }

    fun forget(poll: Poll) {
        if (open[poll.queueKey] === poll) open.remove(poll.queueKey)
    }

    fun abandonAll() {
        open.values.forEach(Poll::abandon)
        open.clear()
    }

    fun vote(voter: Player, queueKey: String, candidateKey: String): Boolean {
        val poll = get(queueKey) ?: return false
        return poll.vote(voter.uniqueId, candidateKey)
    }

    fun votedFor(voter: UUID, queueKey: String): String? = get(queueKey)?.votedFor(voter)

    private fun announceOpened(poll: Poll) {
        Bukkit.getOnlinePlayers().forEach { player -> announceOpenedTo(player, poll) }
    }

    /** Also used by the command, so a player who joins late can still see the ballot. */
    fun announceOpenedTo(player: Player, poll: Poll) {
        player.placeholderComponent(
            "info-poll-opened",
            "queue" replace poll.queueKey,
            "closes-in" replace millisUntil(poll.closesAt)
        ).sendMessage(player)

        val counts = poll.tally
        poll.candidates.forEach { candidate ->
            player.placeholderComponent(
                "format-poll-candidate",
                "queue" replace poll.queueKey,
                "event" replace candidate.key,
                "votes" replace (counts[candidate.key] ?: 0)
            ).replace(
                "event-name" replaceComponent player.placeholderComponent(candidate.displayKey).get()
            ).sendMessage(player)
        }
    }

    fun announceResult(poll: Poll, winner: EventDefinition?) {
        if (winner == null) return
        Bukkit.getOnlinePlayers().forEach { player ->
            player.placeholderComponent(
                "info-poll-closed",
                "queue" replace poll.queueKey,
                "event" replace winner.key,
                "votes" replace (poll.tally[winner.key] ?: 0),
                "total" replace poll.voteCount
            ).sendMessage(player)
        }
    }
}

/**
 * Milliseconds, so the language file formats the countdown with the
 * DateTimeFormat expansion rather than this plugin deciding the shape.
 */
private fun millisUntil(target: Instant): String =
    Duration.between(Instant.now(), target).toMillis().coerceAtLeast(0L).toString()
