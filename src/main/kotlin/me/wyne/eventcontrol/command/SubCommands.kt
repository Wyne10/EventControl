package me.wyne.eventcontrol.command

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.Argument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.executors.CommandExecutor
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.event.EventDefinitionProvider
import me.wyne.eventcontrol.queue.EventQueue
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

//region Shared pieces

internal fun queueArgument(queues: QueueService): Argument<*> =
    StringArgument("queue").replaceSuggestions(
        ArgumentSuggestions.strings { queues.all.map { it.key }.toTypedArray() }
    )

internal fun eventArgument(definitions: EventDefinitionProvider): Argument<*> =
    StringArgument("event").replaceSuggestions(
        ArgumentSuggestions.strings { definitions.map.keys.toTypedArray() }
    )



/** Sends its own error, so a caller reads as `?: return`. */
internal fun resolveQueue(
    sender: CommandSender,
    queues: QueueService,
    args: CommandArguments
): EventQueue? {
    val key = args.get("queue") as String
    val queue = queues[key]
    if (queue == null)
        sender.placeholderComponent("error-unknown-queue", "queue" replace key).sendMessage(sender)
    return queue
}

/**
 * Milliseconds, not a rendered countdown. The language file formats it with the
 * DateTimeFormat expansion, so how a server renders a duration is its choice.
 */
internal fun millisUntil(target: Instant): String =
    Duration.between(Instant.now(), target).toMillis().coerceAtLeast(0L).toString()

internal fun position(slotLocation: org.bukkit.Location?): String =
    slotLocation?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: "-"

//endregion
