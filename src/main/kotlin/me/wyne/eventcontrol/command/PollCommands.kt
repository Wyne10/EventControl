package me.wyne.eventcontrol.command

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.poll.Poll
import me.wyne.eventcontrol.poll.PollService
import me.wyne.wutils.i18n.kotlin.placeholderComponent
import me.wyne.wutils.i18n.kotlin.replace
import org.bukkit.entity.Player

/**
 * Shows the open ballot and records votes.
 *
 * Everything is a literal: CommandAPI refuses a required argument after an
 * optional one, so `poll [queue]` cannot coexist with `poll vote <queue>`.
 */
class PollCommand(private val polls: PollService) : SubCommand("poll") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.event")
        .executesPlayer(PlayerCommandExecutor { player, _ ->
            val poll = polls.all.firstOrNull { it.isOpen }
            if (poll == null) {
                player.placeholderComponent("info-no-poll").sendMessage(player)
                return@PlayerCommandExecutor
            }
            showTo(player, polls, poll)
        })
        .withSubcommand(
            CommandAPICommand("show")
                .withPermission("eventcontrol.event")
                .withArguments(pollQueueArgument(polls))
                .executesPlayer(PlayerCommandExecutor { player, args ->
                    val poll = polls[args.get("queue") as String]
                    if (poll == null) {
                        player.placeholderComponent("info-no-poll").sendMessage(player)
                        return@PlayerCommandExecutor
                    }
                    showTo(player, polls, poll)
                })
        )
        .withSubcommand(
            CommandAPICommand("vote")
                .withPermission("eventcontrol.event")
                .withArguments(pollQueueArgument(polls), pollCandidateArgument(polls))
                .executesPlayer(PlayerCommandExecutor { player, args ->
                    val queueKey = args.get("queue") as String
                    val eventKey = args.get("event") as String
                    if (!polls.vote(player, queueKey, eventKey)) {
                        player.placeholderComponent(
                            "error-poll-vote",
                            "queue" replace queueKey,
                            "event" replace eventKey
                        ).sendMessage(player)
                        return@PlayerCommandExecutor
                    }
                    player.placeholderComponent("success-poll-voted", "event" replace eventKey)
                        .sendMessage(player)
                })
        )

    private companion object {
        fun showTo(player: Player, polls: PollService, poll: Poll) {
            polls.announceOpenedTo(player, poll)
            polls.votedFor(player.uniqueId, poll.queueKey)?.let { current ->
                player.placeholderComponent("info-poll-your-vote", "event" replace current)
                    .sendMessage(player)
            }
        }
    }
}

/** Closes the open ballot early and lets the winner through. */
class PollCloseCommand(private val polls: PollService) : SubCommand("poll") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.poll.manage")
        .withSubcommand(
            CommandAPICommand("close")
                .withPermission("eventcontrol.poll.manage")
                .withArguments(pollQueueArgument(polls))
                .executes(CommandExecutor { sender, args ->
                    val queueKey = args.get("queue") as String
                    val poll = polls[queueKey]
                    if (poll == null) {
                        sender.placeholderComponent("info-no-poll").sendMessage(sender)
                        return@CommandExecutor
                    }
                    val winner = poll.close()
                    polls.forget(poll)
                    polls.announceResult(poll, winner)
                    sender.placeholderComponent(
                        "success-poll-closed",
                        "queue" replace queueKey,
                        "event" replace (winner?.key ?: "-")
                    ).sendMessage(sender)
                    EventControl.logger.info("Poll on queue '{}' closed early by {}", queueKey, sender.name)
                })
        )
}
