package me.wyne.eventcontrol.command

import com.google.inject.Inject
import com.google.inject.Singleton
import dev.jorel.commandapi.CommandAPICommand
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.event.EventDefinitionProvider
import me.wyne.eventcontrol.poll.PollService
import me.wyne.eventcontrol.queue.QueueService

@Singleton
class EventControlCommand @Inject constructor(
    private val plugin: EventControl,
    private val queues: QueueService,
    private val definitions: EventDefinitionProvider,
    private val polls: PollService
) {

    init {
        registerCommand()
        registerEventCommand()
    }

    private fun registerCommand() {
        CommandAPICommand("eventcontrol")
            .withAliases("ec")
            .withSubcommand(ReloadCommand(plugin)())
            .withSubcommand(QueueListCommand(queues)())
            .withSubcommand(QueueInfoCommand(queues)())
            .withSubcommand(QueueStartCommand(queues)())
            .withSubcommand(QueueStopCommand(queues)())
            .withSubcommand(QueueSkipCommand(queues)())
            .withSubcommand(QueueRequestCommand(queues, definitions)())
            .withSubcommand(PollCloseCommand(polls)())
            .register(plugin)
    }

    /** The player-facing half: what is coming up, and when. */
    private fun registerEventCommand() {
        CommandAPICommand("event")
            .withPermission("eventcontrol.event")
            .withSubcommand(UpcomingCommand(queues)())
            .withSubcommand(PollCommand(polls)())
            .executes(dev.jorel.commandapi.executors.CommandExecutor { sender, _ ->
                UpcomingCommand.show(sender, queues)
            })
            .register(plugin)
    }
}
