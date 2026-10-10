package me.wyne.eventcontrol.event

import com.google.inject.Inject
import com.google.inject.Singleton
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.event.ActiveEvent
import me.wyne.eventcontrol.api.event.EventContext
import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.api.event.PrepareContext
import me.wyne.eventcontrol.api.event.PreparedEvent
import me.wyne.eventcontrol.api.spi.AbstractActiveEvent
import me.wyne.eventcontrol.api.spi.AbstractEventType
import me.wyne.eventcontrol.api.spi.AbstractPreparedEvent
import me.wyne.eventcontrol.api.spi.SimpleEventDefinition
import me.wyne.eventcontrol.registry.Registries
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import java.util.concurrent.ThreadLocalRandom

/**
 * The event type that ships in the jar: it resolves a location, and nothing
 * else. It exists so a fresh install has something to run, and because it is a
 * worked example of the one contract that matters — every fact is settled in
 * `prepareBlocking`, so it can be read and shown before the event starts.
 *
 * It registers through the same registry a third-party plugin uses.
 */
@Singleton
class DemoEventType @Inject constructor(registries: Registries) :
    AbstractEventType("demo", EventControl.logger) {

    init {
        registries.eventTypes().register(this)
    }

    override fun readDefinition(definitionKey: String, config: ConfigurationSection): EventDefinition =
        DemoDefinition(definitionKey, this, config)

    override fun prepareBlocking(definition: EventDefinition, context: PrepareContext): PreparedEvent {
        val demo = definition as DemoDefinition
        val random = ThreadLocalRandom.current()
        val spawn = context.world.spawnLocation

        val x = spawn.blockX + random.nextInt(-demo.radius, demo.radius + 1)
        val z = spawn.blockZ + random.nextInt(-demo.radius, demo.radius + 1)
        // Picking a surface Y needs the main thread, so the same budget a real
        // search would use is spent here instead of after the event starts.
        val location = Location(context.world, x.toDouble(), spawn.y, z.toDouble())

        logger.debug("Prepared demo event '{}' at {}, {}", demo.key, x, z)
        return DemoPreparedEvent(demo, location, context.startAt)
    }
}

private class DemoDefinition(
    key: String,
    type: DemoEventType,
    config: ConfigurationSection
) : SimpleEventDefinition(key, type, config) {

    val radius: Int = config.getInt("radius", 200).coerceAtLeast(1)
}

private class DemoPreparedEvent(
    private val demo: DemoDefinition,
    private val location: Location,
    startAt: java.time.Instant
) : AbstractPreparedEvent(demo, startAt) {

    override fun getLocation(): Location = location

    override fun onStart(context: EventContext): ActiveEvent = DemoActiveEvent(this, context)

    override fun onRelease() {
        // Nothing was placed in the world, so there is nothing to roll back —
        // a type that pasted terrain would undo it here.
    }
}

private class DemoActiveEvent(
    prepared: PreparedEvent,
    context: EventContext
) : AbstractActiveEvent(prepared, context)
