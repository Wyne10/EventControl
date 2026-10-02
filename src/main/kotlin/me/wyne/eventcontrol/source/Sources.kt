package me.wyne.eventcontrol.source

import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.api.queue.QueueSlot
import me.wyne.eventcontrol.api.queue.QueueSource
import me.wyne.eventcontrol.api.queue.QueueView
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom

/** Walks the configured events in order, round robin. */
class SequentialSource : QueueSource {

    private var cursor = 0

    override fun nextFor(queue: QueueView, slot: QueueSlot): CompletableFuture<EventDefinition> {
        val candidates = queue.candidates
        if (candidates.isEmpty()) return CompletableFuture.completedFuture(null)
        val chosen = candidates[cursor % candidates.size]
        cursor = (cursor + 1) % candidates.size
        return CompletableFuture.completedFuture(chosen)
    }

    override fun reset() {
        cursor = 0
    }
}

/**
 * Picks by weight, avoiding the last few choices so a small pool does not
 * repeat itself.
 */
class WeightedRandomSource(private val avoidRepeats: Int) : QueueSource {

    private val recent = ArrayDeque<String>()

    override fun nextFor(queue: QueueView, slot: QueueSlot): CompletableFuture<EventDefinition> {
        val eligible = queue.candidates.filter { it.weight > 0 }
        if (eligible.isEmpty()) return CompletableFuture.completedFuture(null)

        val preferred = eligible.filterNot { recent.contains(it.key) }
        val pool = preferred.ifEmpty { eligible }

        val total = pool.sumOf { it.weight.toLong() }
        var roll = ThreadLocalRandom.current().nextLong(total)
        var chosen = pool.last()
        for (candidate in pool) {
            roll -= candidate.weight
            if (roll < 0) {
                chosen = candidate
                break
            }
        }

        remember(chosen.key, eligible.size)
        return CompletableFuture.completedFuture(chosen)
    }

    private fun remember(key: String, poolSize: Int) {
        if (avoidRepeats <= 0) return
        // Never remember so much that nothing stays eligible.
        val window = minOf(avoidRepeats, poolSize - 1).coerceAtLeast(0)
        if (window == 0) return
        recent.addLast(key)
        while (recent.size > window) recent.removeFirst()
    }

    override fun reset() {
        recent.clear()
    }
}
