package me.wyne.eventcontrol.module

import com.google.inject.AbstractModule
import me.wyne.eventcontrol.EventControl
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.Plugin

class PluginModule(private val plugin: EventControl) : AbstractModule() {
    override fun configure() {
        bind(EventControl::class.java)
            .toInstance(plugin)
        bind(Plugin::class.java)
            .toInstance(plugin)
        bind(FileConfiguration::class.java)
            .toInstance(plugin.getConfig())
    }
}
