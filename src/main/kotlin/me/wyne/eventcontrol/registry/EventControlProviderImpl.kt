package me.wyne.eventcontrol.registry

import com.google.inject.Inject
import com.google.inject.Singleton
import me.wyne.eventcontrol.api.EventControlProvider
import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.api.queue.QueueView
import me.wyne.eventcontrol.event.EventDefinitionProvider
import me.wyne.eventcontrol.queue.QueueService

@Singleton
class EventControlProviderImpl @Inject constructor(
    private val queues: QueueService,
    private val definitions: EventDefinitionProvider
) : EventControlProvider {

    override fun getQueues(): Collection<QueueView> = queues.all

    override fun getQueue(queueKey: String): QueueView? = queues[queueKey]

    override fun getDefinition(definitionKey: String): EventDefinition? = definitions[definitionKey]

    override fun getDefinitions(): Collection<EventDefinition> = definitions.map.values
}
