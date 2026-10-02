package me.wyne.eventcontrol.source

import com.google.inject.Inject
import com.google.inject.Singleton
import me.wyne.eventcontrol.registry.Registries

/**
 * Registers the sources that ship in the jar through the same registry a third
 * party uses, so a built-in is not a special case.
 */
@Singleton
class BuiltInSources @Inject constructor(registries: Registries) {

    init {
        registries.queueSources().register("sequential") { _, _ -> SequentialSource() }
        registries.queueSources().register("weighted-random") { key, config ->
            WeightedRandomSource(config.getInt("$key-avoid-repeats", DEFAULT_AVOID_REPEATS))
        }
    }

    private companion object {
        const val DEFAULT_AVOID_REPEATS = 1
    }
}
