package me.wyne.eventcontrol.module

import com.google.inject.AbstractModule
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.command.EventControlCommand
import me.wyne.eventcontrol.placeholder.QueuePlaceholders

//region Implementations

private class OptionalModule(
    private val className: String,
    private val exceptionMessage: String = "$className not found, module ignored",
    private vararg val modules: Class<out Any>
) : AbstractModule() {
    override fun configure() {
        try {
            Class.forName(className)
            modules.forEach { bind(it) }
        } catch (_: ClassNotFoundException) {
            EventControl.logger.warn(exceptionMessage)
        }
    }
}

private class ConfigurableModule(
    private val configurationPath: String,
    private vararg val modules: Class<out Any>
) : AbstractModule() {
    override fun configure() {
        val isActive = EventControl.instance.config.getBoolean(configurationPath, false)
        if (isActive)
            modules.forEach { bind(it) }
    }
}

//endregion

val PlaceholderModule: AbstractModule = OptionalModule(
    className = "me.clip.placeholderapi.PlaceholderAPI",
    exceptionMessage = "PlaceholderAPI not found, placeholders are not registered",
    QueuePlaceholders::class.java
)

val CommandModule: AbstractModule = OptionalModule(
    className = "dev.jorel.commandapi.CommandAPI",
    exceptionMessage = "CommandAPI not found, commands are not registered",
    EventControlCommand::class.java
)
