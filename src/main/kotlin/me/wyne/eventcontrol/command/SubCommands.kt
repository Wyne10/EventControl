package me.wyne.eventcontrol.command

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.Argument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.queue.SlotState
import me.wyne.eventcontrol.event.EventDefinitionProvider
import me.wyne.eventcontrol.queue.EventQueue
import me.wyne.eventcontrol.poll.PollService
import me.wyne.eventcontrol.queue.QueueService
import me.wyne.wutils.i18n.kotlin.placeholderComponent
import me.wyne.wutils.i18n.kotlin.replace
import org.bukkit.command.CommandSender
import java.time.Duration
import java.time.Instant

abstract class SubCommand(argument: String) {
    open val command = CommandAPICommand(argument)
    operator fun invoke() = command
}

class ReloadCommand(plugin: EventControl) : SubCommand("reload") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.reload")
        .executes(CommandExecutor { sender, _ ->
            plugin.reload()
            sender.placeholderComponent("success-plugin-reload").sendMessage(sender)
            EventControl.logger.info("Plugin reloaded")
        })
}

class QueueListCommand(private val queues: QueueService) : SubCommand("queues") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.queue.view")
        .executes(CommandExecutor { sender, _ ->
            if (queues.all.isEmpty()) {
                sender.placeholderComponent("info-no-queues").sendMessage(sender)
                return@CommandExecutor
            }
            queues.all.forEach { queue ->
                sender.placeholderComponent(
                    "format-queue-entry",
                    "queue" replace queue.key,
                    "state" replace if (queue.isRunning) "running" else "stopped",
                    "upcoming" replace queue.upcoming.size
                ).sendMessage(sender)
            }
        })
}

class QueueInfoCommand(private val queues: QueueService) : SubCommand("info") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.queue.view")
        .withArguments(queueArgument(queues))
        .executes(CommandExecutor { sender, args ->
            val queue = resolve(sender, queues, args) ?: return@CommandExecutor
            sender.placeholderComponent(
                "format-queue-header",
                "queue" replace queue.key,
                "state" replace if (queue.isRunning) "running" else "stopped"
            ).sendMessage(sender)

            queue.upcoming.forEachIndexed { index, slot ->
                val definition = slot.definition
                sender.placeholderComponent(
                    "format-slot-entry",
                    "index" replace index,
                    "event" replace (definition?.key ?: "-"),
                    "state" replace slot.state.name.lowercase(),
                    "origin" replace slot.origin.name.lowercase(),
                    "starts-in" replace countdown(slot.startAt),
                    "position" replace (slot.prepared?.location
                        ?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: "-")
                ).sendMessage(sender)
            }
        })
}

class QueueStartCommand(private val queues: QueueService) : SubCommand("start") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.queue.control")
        .withArguments(queueArgument(queues))
        .executes(CommandExecutor { sender, args ->
            val queue = resolve(sender, queues, args) ?: return@CommandExecutor
            queue.start()
            sender.placeholderComponent("success-queue-started", "queue" replace queue.key)
                .sendMessage(sender)
            EventControl.logger.info("Queue '{}' started by {}", queue.key, sender.name)
        })
}

class QueueStopCommand(private val queues: QueueService) : SubCommand("stop") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.queue.control")
        .withArguments(queueArgument(queues))
        .executes(CommandExecutor { sender, args ->
            val queue = resolve(sender, queues, args) ?: return@CommandExecutor
            queue.stop()
            sender.placeholderComponent("success-queue-stopped", "queue" replace queue.key)
                .sendMessage(sender)
            EventControl.logger.info("Queue '{}' stopped by {}", queue.key, sender.name)
        })
}

/**
 * Abandons the head slot. Unlike the stop-then-start this replaces, it advances
 * the timeline by exactly one position and releases whatever the skipped
 * preparation had already allocated.
 */
class QueueSkipCommand(private val queues: QueueService) : SubCommand("skip") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.queue.control")
        .withArguments(queueArgument(queues))
        .executes(CommandExecutor { sender, args ->
            val queue = resolve(sender, queues, args) ?: return@CommandExecutor
            val skipped = queue.skip()
            if (skipped == null) {
                sender.placeholderComponent("error-queue-empty", "queue" replace queue.key)
                    .sendMessage(sender)
                return@CommandExecutor
            }
            sender.placeholderComponent(
                "success-queue-skipped",
                "queue" replace queue.key,
                "event" replace skipped.key
            ).sendMessage(sender)
            EventControl.logger.info("Queue '{}' skipped '{}' for {}", queue.key, skipped.key, sender.name)
        })
}

class QueueRequestCommand(
    private val queues: QueueService,
    private val definitions: EventDefinitionProvider
) : SubCommand("request") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.queue.control")
        .withArguments(queueArgument(queues), eventArgument(definitions))
        .executes(CommandExecutor { sender, args ->
            val queue = resolve(sender, queues, args) ?: return@CommandExecutor
            val eventKey = args.get("event") as String
            val definition = definitions[eventKey]
            if (definition == null) {
                sender.placeholderComponent("error-unknown-event", "event" replace eventKey)
                    .sendMessage(sender)
                return@CommandExecutor
            }
            val slot = queue.request(definition, sender.name)
            sender.placeholderComponent(
                "success-event-requested",
                "queue" replace queue.key,
                "event" replace definition.key,
                "starts-in" replace countdown(slot.startAt)
            ).sendMessage(sender)
            EventControl.logger.info(
                "Queue '{}' got a request for '{}' from {}", queue.key, definition.key, sender.name
            )
        })
}

/** What players see: the prepared lookahead, with real detail. */
class UpcomingCommand(private val queues: QueueService) : SubCommand("upcoming") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.event")
        .executes(CommandExecutor { sender, _ -> show(sender, queues) })

    companion object {
        fun show(sender: CommandSender, queues: QueueService) {
            val running = queues.all.filter { it.isRunning }
            if (running.isEmpty()) {
                sender.placeholderComponent("info-no-queues").sendMessage(sender)
                return
            }
            running.forEach { queue ->
                queue.activeSlot?.prepared?.let { prepared ->
                    sender.placeholderComponent(
                        "info-event-active",
                        "event" replace prepared.definition.key,
                        "ends-in" replace countdown(prepared.endAt),
                        "position" replace (prepared.location
                            ?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: "-")
                    ).sendMessage(sender)
                }
                queue.upcoming
                    .filter { it.state != SlotState.RUNNING }
                    .take(queue.lookahead)
                    .forEach { slot ->
                        val definition = slot.definition ?: return@forEach
                        sender.placeholderComponent(
                            "info-event-upcoming",
                            "event" replace definition.key,
                            "starts-in" replace countdown(slot.startAt),
                            "position" replace (slot.prepared?.location
                                ?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: "-")
                        ).sendMessage(sender)
                    }
            }
        }
    }
}

//region Shared pieces

private fun queueArgument(queues: QueueService): Argument<*> =
    StringArgument("queue").replaceSuggestions(
        ArgumentSuggestions.strings { queues.all.map { it.key }.toTypedArray() }
    )

private fun pollQueueArgument(polls: PollService): Argument<*> =
    StringArgument("queue").replaceSuggestions(
        ArgumentSuggestions.strings { polls.all.filter { it.isOpen }.map { it.queueKey }.toTypedArray() }
    )

private fun pollCandidateArgument(polls: PollService): Argument<*> =
    StringArgument("event").replaceSuggestions(
        ArgumentSuggestions.strings { info ->
            val queueKey = info.previousArgs().get("queue") as String?
            queueKey?.let { polls[it] }?.candidates?.map { it.key }?.toTypedArray() ?: emptyArray()
        }
    )

private fun eventArgument(definitions: EventDefinitionProvider): Argument<*> =
    StringArgument("event").replaceSuggestions(
        ArgumentSuggestions.strings { definitions.map.keys.toTypedArray() }
    )

/** Sends its own error, so a caller reads as `?: return`. */
private fun resolve(
    sender: CommandSender,
    queues: QueueService,
    args: dev.jorel.commandapi.executors.CommandArguments
): EventQueue? {
    val key = args.get("queue") as String
    val queue = queues[key]
    if (queue == null)
        sender.placeholderComponent("error-unknown-queue", "queue" replace key).sendMessage(sender)
    return queue
}

internal fun countdown(target: Instant): String {
    val seconds = Duration.between(Instant.now(), target).seconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}

//endregion

//region Polls

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
                    val queueKey = args.get("queue") as String
                    val poll = polls[queueKey]
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
        fun showTo(player: org.bukkit.entity.Player, polls: PollService, poll: me.wyne.eventcontrol.poll.Poll) {
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
                .withArguments(
                    StringArgument("queue").replaceSuggestions(
                        ArgumentSuggestions.strings { polls.all.map { it.queueKey }.toTypedArray() }
                    )
                )
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
                    EventControl.logger.info(
                        "Poll on queue '{}' closed early by {}", queueKey, sender.name
                    )
                })
        )
}

//endregion
