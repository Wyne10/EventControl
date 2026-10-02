package me.wyne.eventcontrol

import com.google.gson.GsonBuilder
import com.google.inject.Injector
import me.wyne.wutils.common.plugin.CompositeJavaPlugin
import org.bukkit.configuration.MemoryConfiguration
import org.slf4j.Logger

class EventControl : CompositeJavaPlugin<EventControl>() {

    lateinit var injector: Injector
        internal set

    override fun init() {
        instance = this
        addSteps(
            LoadDefaultConfig,
            InitializeLogger,
            InitializeJson(GsonBuilder().setPrettyPrinting().create(), dataFolder),
            InitializeI18n,
            InitializeInjector,
            InitializeConfig,
            InitializeLoader,
            InstallApi,
            Load,
            Disable,
            Reload
        )
    }

    companion object {
        lateinit var instance: EventControl
            private set
        lateinit var logger: Logger
            internal set
        val EMPTY_CONFIGURATION = MemoryConfiguration()
    }

}