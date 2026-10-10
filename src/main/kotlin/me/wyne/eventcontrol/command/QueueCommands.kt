package me.wyne.eventcontrol.command

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.CommandExecutor
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.queue.SlotState
import me.wyne.eventcontrol.event.EventDefinitionProvider
import me.wyne.eventcontrol.queue.QueueService
import me.wyne.wutils.i18n.kotlin.placeholderComponent
import me.wyne.wutils.i18n.kotlin.replace
import org.bukkit.command.CommandSender

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
            val queue = resolveQueue(sender, queues, args) ?: return@CommandExecutor
            sender.placeholderComponent(
                "format-queue-header",
                "queue" replace queue.key,
                "state" replace if (queue.isRunning) "running" else "stopped"
            ).sendMessage(sender)

            queue.upcoming.forEachIndexed { index, slot ->
                sender.placeholderComponent(
                    "format-slot-entry",
                    "index" replace index,
                    "event" replace (slot.definition?.key ?: "-"),
                    "state" replace slot.state.name.lowercase(),
                    "origin" replace slot.origin.name.lowercase(),
                    "starts-in" replace millisUntil(slot.startAt),
                    "position" replace position(slot.prepared?.location)
                ).sendMessage(sender)
            }
        })
}

class QueueStartCommand(private val queues: QueueService) : SubCommand("start") {
    override val command: CommandAPICommand = super.command
        .withPermission("eventcontrol.queue.control")
        .withArguments(queueArgument(queues))
        .executes(CommandExecutor { sender, args ->
            val queue = resolveQueue(sender, queues, args) ?: return@CommandExecutor
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
            val queue = resolveQueue(sender, queues, args) ?: return@CommandExecutor
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
            val queue = resolveQueue(sender, queues, args) ?: return@CommandExecutor
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
            val queue = resolveQueue(sender, queues, args) ?: return@CommandExecutor
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
                "starts-in" replace millisUntil(slot.startAt)
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
                        "ends-in" replace millisUntil(prepared.endAt),
                        "position" replace position(prepared.location)
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
                            "starts-in" replace millisUntil(slot.startAt),
                            "position" replace position(slot.prepared?.location)
                        ).sendMessage(sender)
                    }
            }
        }
    }
}
