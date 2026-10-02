package me.wyne.eventcontrol.module

import com.google.inject.AbstractModule
import me.wyne.eventcontrol.event.Announcer
import me.wyne.eventcontrol.event.DemoEventType
import me.wyne.eventcontrol.event.EventDefinitionProvider
import me.wyne.eventcontrol.poll.PollService
import me.wyne.eventcontrol.queue.QueueProvider
import me.wyne.eventcontrol.queue.QueueService
import me.wyne.eventcontrol.registry.EventControlProviderImpl
import me.wyne.eventcontrol.registry.Registries
import me.wyne.eventcontrol.source.BuiltInSources

//region Implementations

private class CoreModule(private vararg val modules: Class<out Any>) : AbstractModule() {
    override fun configure() {
        modules.forEach { bind(it) }
    }
}

//endregion

val RegistryModule: AbstractModule = CoreModule(
    Registries::class.java,
    EventControlProviderImpl::class.java
)

val EventModule: AbstractModule = CoreModule(
    DemoEventType::class.java,
    EventDefinitionProvider::class.java,
    Announcer::class.java
)

val QueueModule: AbstractModule = CoreModule(
    BuiltInSources::class.java,
    PollService::class.java,
    QueueProvider::class.java,
    QueueService::class.java
)
