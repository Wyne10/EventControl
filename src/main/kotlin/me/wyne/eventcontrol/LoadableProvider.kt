package me.wyne.eventcontrol

import me.wyne.wutils.common.loadable.Loadable
import me.wyne.wutils.common.loadable.Loader
import me.wyne.wutils.config.configurables.attribute.GenericFactory
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

@Suppress("LeakingThis")
open class LoadableProvider<V>(protected val sectionKey: String, protected val factory: GenericFactory<V>) : Loadable {

    protected val loadedMap = mutableMapOf<String, V>()
    val directory = File(EventControl.instance.dataFolder, sectionKey)

    val map
        get() = loadedMap.toMap()

    init {
        Loader.global.registerLoadable(this)
    }

    operator fun get(key: String): V? =
        loadedMap[key]

    override fun load(config: ConfigurationSection) {
        loadedMap.clear()
        val section = config.getConfigurationSection(sectionKey) ?: return
        section.getKeys(false).forEach { key ->
            EventControl.logger.debug("Loading key '{}' from '{}'", key, sectionKey)
            runCatching {
                loadedMap[key] = factory.create(key, section)
            }.onFailure { EventControl.logger.error("Failed loading '{}' from '{}'", key, sectionKey, it) }
        }
    }

    fun loadFiles(directory: File) {
        loadFilesRecursive(directory, directory, "")
    }

    private fun loadFilesRecursive(baseDirectory: File, directory: File, relativePath: String) {
        if (!directory.exists())
            directory.mkdirs()
        directory.listFiles()?.let { files ->
            files.filter { it.isDirectory }
                .forEach {
                    val newPath = if (relativePath.isEmpty()) it.name else "$relativePath/${it.name}"
                    loadFilesRecursive(baseDirectory, it, newPath)
                }
            files.filter { it.isFile }
                .forEach { file ->
                    val key: String = if (relativePath.isEmpty())
                        file.nameWithoutExtension
                    else
                        "$relativePath/${file.nameWithoutExtension}"
                    EventControl.logger.debug("Loading key '{}' from '{}'", file.nameWithoutExtension, "${baseDirectory.name}/$relativePath")
                    runCatching {
                        loadedMap[key] = factory.create(key, YamlConfiguration.loadConfiguration(file))
                    }.onFailure { EventControl.logger.error("Failed loading '{}' from '{}'", file.nameWithoutExtension, "${baseDirectory.name}/$relativePath", it) }
                }
        }
    }

}