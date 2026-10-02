package me.wyne.eventcontrol.poll

import me.wyne.eventcontrol.api.event.EventDefinition
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * One open ballot. Candidates are real [EventDefinition]s, so a ballot can show
 * what each option actually is — which is only possible because the engine
 * resolves events ahead of time rather than at start.
 */
class Poll(
    val queueKey: String,
    val candidates: List<EventDefinition>,
    val closesAt: Instant,
    private val result: CompletableFuture<EventDefinition>
) {

    private val votes = linkedMapOf<UUID, String>()

    val isOpen: Boolean
        get() = !result.isDone

    /** Tally per candidate key, in candidate order, including the zeroes. */
    val tally: Map<String, Int>
        get() = candidates.associate { candidate ->
            candidate.key to votes.values.count { it == candidate.key }
        }

    val voteCount: Int
        get() = votes.size

    fun votedFor(voter: UUID): String? = votes[voter]

    /**
     * Records a vote, replacing that voter's previous one. Returns false for an
     * unknown candidate or a closed poll.
     */
    fun vote(voter: UUID, candidateKey: String): Boolean {
        if (!isOpen) return false
        if (candidates.none { it.key == candidateKey }) return false
        votes[voter] = candidateKey
        return true
    }

    /**
     * Closes the ballot and resolves the winner. A tie, or no votes at all, is
     * broken by weight and then by candidate order, so a poll nobody voted in
     * still produces an event rather than stalling the queue.
     */
    fun close(): EventDefinition? {
        if (!isOpen) return null
        val counts = tally
        val winner = candidates.maxWithOrNull(
            compareBy({ counts[it.key] ?: 0 }, { it.weight })
        )
        result.complete(winner)
        return winner
    }

    fun abandon() {
        if (isOpen) result.complete(null)
    }
}
