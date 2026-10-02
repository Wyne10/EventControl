package me.wyne.eventcontrol.poll

import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.api.queue.QueueSlot
import me.wyne.eventcontrol.api.queue.QueueSource
import me.wyne.eventcontrol.api.queue.QueueView
import me.wyne.eventcontrol.api.spi.Durations
import me.wyne.wutils.common.scheduler.Schedulers
import org.bukkit.configuration.ConfigurationSection
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Lets players decide what runs next. A poll is a queue source like any other:
 * the engine asks it to fill a slot and waits, the same way it waits for a
 * weighted roll.
 */
class PollSource(
    private val service: PollService,
    private val window: Duration,
    private val candidateCount: Int
) : QueueSource {

    override fun nextFor(queue: QueueView, slot: QueueSlot): CompletableFuture<EventDefinition> {
        val candidates = queue.candidates
            .filter { it.weight > 0 }
            .shuffled()
            .take(candidateCount)

        if (candidates.size < 2) {
            // Nothing to choose between; fall back rather than open a one-horse poll.
            EventControl.logger.debug(
                "Queue '{}' has fewer than two candidates, skipping the poll", queue.key
            )
            return CompletableFuture.completedFuture(candidates.firstOrNull())
        }

        val result = CompletableFuture<EventDefinition>()
        val poll = Poll(queue.key, candidates, Instant.now().plus(window), result)
        service.open(poll)

        Schedulers.sync().runLater({
            val winner = poll.close()
            service.forget(poll)
            if (winner != null)
                EventControl.logger.info(
                    "Poll on queue '{}' chose '{}' with {} vote(s)",
                    queue.key, winner.key, poll.tally[winner.key] ?: 0
                )
            service.announceResult(poll, winner)
        }, window.toMillis(), TimeUnit.MILLISECONDS)

        return result
    }

    override fun reset() = service.abandonAll()

    companion object Factory {

        fun create(service: PollService, key: String, config: ConfigurationSection): PollSource {
            val window = Durations.read(config, "$key-window", Duration.ofMinutes(2))
            val candidates = config.getInt("$key-candidates", 3).coerceAtLeast(2)
            return PollSource(service, window, candidates)
        }
    }
}
