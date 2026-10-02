package me.wyne.eventcontrol.queue

import com.google.inject.Inject
import com.google.inject.Singleton
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.LoadableProvider
import me.wyne.wutils.common.kotlin.config.saveDirectoryResource
import me.wyne.wutils.common.loadable.LoadableMeta
import org.bukkit.configuration.ConfigurationSection

@Singleton
@LoadableMeta(priority = 1)
class QueueProvider @Inject constructor(plugin: EventControl) :
    LoadableProvider<QueueDefinition>("queue", QueueDefinition.Factory) {

    init {
        plugin.saveDirectoryResource(directory)
    }

    override fun load(config: ConfigurationSection) {
        super.load(config)
        loadFiles(directory)
    }
}
