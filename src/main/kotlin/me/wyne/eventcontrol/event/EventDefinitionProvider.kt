package me.wyne.eventcontrol.event

import com.google.inject.Inject
import com.google.inject.Singleton
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.LoadableProvider
import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.registry.Registries
import me.wyne.eventcontrol.registry.isValidKey
import me.wyne.wutils.common.kotlin.config.saveDirectoryResource
import me.wyne.wutils.common.kotlin.config.deep
import me.wyne.wutils.common.loadable.LoadableMeta
import me.wyne.wutils.config.configurables.attribute.GenericFactory
import org.bukkit.configuration.ConfigurationSection

@Singleton
@LoadableMeta(priority = 0)
class EventDefinitionProvider @Inject constructor(
    plugin: EventControl,
    registries: Registries
) : LoadableProvider<EventDefinition>("event", DefinitionFactory(registries)) {

    init {
        plugin.saveDirectoryResource(directory)
    }

    override fun load(config: ConfigurationSection) {
        super.load(config)
        loadFiles(directory)
    }
}

private class DefinitionFactory(private val registries: Registries) : GenericFactory<EventDefinition> {

    override fun create(key: String, config: ConfigurationSection): EventDefinition {
        require(isValidKey(key)) {
            "Event key '$key' must match [a-z0-9-]+ so it can be addressed in a placeholder"
        }
        val section = config.deep(key)
        val typeKey = requireNotNull(section.getString("type")) { "Event '$key' has no 'type'" }
        val type = requireNotNull(registries.eventTypes().get(typeKey)) {
            "Event '$key' names unknown type '$typeKey'; registered types: ${registries.eventTypes().keys()}"
        }
        return type.readDefinition(key, section)
    }
}
