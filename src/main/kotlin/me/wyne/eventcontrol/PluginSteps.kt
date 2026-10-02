package me.wyne.eventcontrol

import com.google.gson.Gson
import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.Stage
import me.wyne.wutils.common.loadable.Loader
import me.wyne.wutils.common.plugin.CompositeStep
import me.wyne.wutils.common.plugin.LevelWrapper
import me.wyne.wutils.common.plugin.LoggerWrapper
import me.wyne.wutils.common.plugin.PluginStep
import me.wyne.wutils.common.plugin.Step
import me.wyne.wutils.common.plugin.StepScope
import me.wyne.wutils.config.Config
import me.wyne.wutils.i18n.I18n
import me.wyne.wutils.i18n.PluginI18nBuilder
import me.wyne.wutils.i18n.language.component.BukkitComponentAudiences
import me.wyne.wutils.i18n.language.interpretation.ComponentInterpreters
import me.wyne.wutils.i18n.language.validation.EmptyValidator
import me.wyne.wutils.common.scheduler.Schedulers
import me.wyne.wutils.json.JsonRegistry
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import me.wyne.eventcontrol.EventControl.Companion.EMPTY_CONFIGURATION
import me.wyne.eventcontrol.EventControl.Companion.logger
import me.wyne.eventcontrol.api.EventControlApi
import me.wyne.eventcontrol.module.CommandModule
import me.wyne.eventcontrol.module.EventModule
import me.wyne.eventcontrol.module.QueueModule
import me.wyne.eventcontrol.module.RegistryModule
import me.wyne.eventcontrol.registry.EventControlProviderImpl
import me.wyne.eventcontrol.queue.QueueService
import me.wyne.eventcontrol.registry.Registries
import me.wyne.eventcontrol.module.PlaceholderModule
import me.wyne.eventcontrol.module.PluginModule
import java.io.File

@Step(priority = 0, scope = StepScope.ENABLE)
object LoadDefaultConfig : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        plugin.saveDefaultConfig()
        plugin.config.setDefaults(EMPTY_CONFIGURATION)
    }
}

@Step(priority = 1, scope = StepScope.ENABLE)
object InitializeLogger : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        logger = LoggerWrapper(plugin.slF4JLogger, LevelWrapper.valueOf(plugin.config.getString("logLevel", "INFO")!!))
    }
}

@Step(priority = 2, scope = StepScope.ENABLE)
class InitializeJson(private val gson: Gson, private val directory: File) : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        JsonRegistry.global = JsonRegistry(gson, directory)
    }
}

@Step(priority = 3, scope = StepScope.ENABLE)
object InitializeI18n : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        I18n.global = PluginI18nBuilder(plugin)
            .setLogger(logger)
            .setComponentAudience(BukkitComponentAudiences(BukkitAudiences.create(plugin)))
            .setComponentInterpreter(
                ComponentInterpreters.valueOf(
                    plugin.config.getString("serializer", "MINI_MESSAGE")!!
                ).get(EmptyValidator())
            )
            .setUsePlayerLanguage(plugin.config.getBoolean("usePlayerLanguage", true))
            .loadLanguage("lang/ru.yml")
            .build()
    }
}

@Step(priority = 4, scope = StepScope.ENABLE)
object InitializeInjector : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        try {
            EventControl.instance.injector = Guice.createInjector(
                Stage.PRODUCTION,
                PluginModule(plugin),
                RegistryModule,
                EventModule,
                QueueModule,
                PlaceholderModule,
                CommandModule
            )
        } catch (e: CreationException) {
            // Guice's own formatter reads class files to build its message, which
            // fails once Bukkit has closed the plugin classloader — by then the
            // reason is gone. Log each error on its own, while the jar is open.
            logger.error("Guice injector creation failed with {} error(s)", e.errorMessages.size)
            e.errorMessages.forEach { logger.error("  {}", it.message) }
        }
    }
}

@Step(priority = 5, scope = StepScope.ENABLE)
object InitializeConfig : CompositeStep<EventControl>(ReloadConfig) {
    override fun before(plugin: EventControl) {
        Config.global.apply {
            logger = EventControl.logger
            setConfigGenerator(plugin, "config.yml")
            generateConfig()
        }
    }
}

@Step(priority = 6, scope = StepScope.ENABLE)
object InitializeLoader : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        Loader.global.registerConfig(Loader.DEFAULT_PATH, plugin.config)
    }
}

/**
 * Publishes the API before any plugin that depends on EventControl enables, so
 * its onEnable can register into the open registries.
 */
@Step(priority = 7, scope = StepScope.ENABLE)
object InstallApi : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        val injector = plugin.injector
        EventControlApi.install(
            injector.getInstance(EventControlProviderImpl::class.java),
            injector.getInstance(Registries::class.java)
        )
    }
}

@Step(priority = 8, scope = StepScope.ENABLE)
object Load : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        JsonRegistry.global.load()
        val registries = plugin.injector.getInstance(Registries::class.java)
        if (!registries.isOpen) {
            // A reload: the registries closed long ago and types already stand.
            Loader.global.load(plugin)
            return
        }
        // First enable. Definitions name event types by key, so they cannot be
        // read until every depending plugin has had its onEnable. One tick after
        // the server finishes enabling is exactly that moment.
        //
        // This must be runLater, not run: a sync scheduler called from the main
        // thread runs the body inline, which would close the registries during
        // this plugin's own onEnable and refuse every type a dependent registers.
        Schedulers.sync().runLater({
            registries.close()
            Loader.global.load(plugin)
        }, 1L)
    }
}

@Step(scope = StepScope.DISABLE)
object Disable : CompositeStep<EventControl>(CaptureQueueState, Write)

@Step(scope = StepScope.RELOAD)
object Reload : CompositeStep<EventControl>(ReloadConfig, InitializeLogger, InitializeLoader, InitializeI18n, Write, Load)

object CaptureQueueState : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        plugin.injector.getInstance(QueueService::class.java).captureStateForWrite()
    }
}

object Write : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        JsonRegistry.global.write()
    }
}

object ReloadConfig : PluginStep<EventControl> {
    override fun run(plugin: EventControl) {
        plugin.reloadConfig()
        plugin.config.setDefaults(EMPTY_CONFIGURATION)
        Config.global.reloadConfig(plugin.config)
    }
}