package me.wyne.eventcontrol.registry

import com.google.inject.Inject
import com.google.inject.Singleton
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.EventControlRegistries
import me.wyne.eventcontrol.api.event.EventType
import me.wyne.eventcontrol.api.queue.QueueSource
import me.wyne.eventcontrol.api.spi.ConfigFactory
import me.wyne.eventcontrol.api.spi.FactoryRegistry
import me.wyne.eventcontrol.api.spi.InstanceRegistry

@Singleton
class Registries @Inject constructor() : EventControlRegistries {

    private var open = true

    private val eventTypes = KeyedInstanceRegistry("event type", EventType::getKey) { open }
    private val queueSources = KeyedFactoryRegistry<QueueSource>("queue source") { open }

    override fun eventTypes(): InstanceRegistry<EventType> = eventTypes

    override fun queueSources(): FactoryRegistry<QueueSource> = queueSources

    override fun isOpen(): Boolean = open

    fun close() {
        if (!open) return
        open = false
        EventControl.logger.info(
            "Registries closed with {} event type(s) and {} queue source(s)",
            eventTypes.keys().size,
            queueSources.keys().size
        )
    }
}

private class KeyedInstanceRegistry<T : Any>(
    private val subject: String,
    private val keyOf: (T) -> String,
    private val isOpen: () -> Boolean
) : InstanceRegistry<T> {

    private val values = linkedMapOf<String, T>()

    override fun register(value: T) {
        val key = keyOf(value)
        if (!isOpen()) {
            EventControl.logger.warn(
                "Refused {} '{}': the registries closed before it was registered", subject, key
            )
            return
        }
        if (!isValidKey(key)) {
            EventControl.logger.error(
                "Refused {} '{}': a key must match {}", subject, key, KEY_PATTERN.pattern
            )
            return
        }
        values.put(key, value)?.let {
            EventControl.logger.warn("Replaced an already registered {} '{}'", subject, key)
        }
        EventControl.logger.debug("Registered {} '{}'", subject, key)
    }

    override fun get(key: String): T? = values[key]

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun keys(): Set<String> = values.keys.toSet()
}

private class KeyedFactoryRegistry<T : Any>(
    private val subject: String,
    private val isOpen: () -> Boolean
) : FactoryRegistry<T> {

    private val factories = linkedMapOf<String, ConfigFactory<T>>()

    override fun register(key: String, factory: ConfigFactory<T>) {
        if (!isOpen()) {
            EventControl.logger.warn(
                "Refused {} '{}': the registries closed before it was registered", subject, key
            )
            return
        }
        if (!isValidKey(key)) {
            EventControl.logger.error(
                "Refused {} '{}': a key must match {}", subject, key, KEY_PATTERN.pattern
            )
            return
        }
        factories.put(key, factory)?.let {
            EventControl.logger.warn("Replaced an already registered {} '{}'", subject, key)
        }
        EventControl.logger.debug("Registered {} '{}'", subject, key)
    }

    override fun get(key: String): ConfigFactory<T>? = factories[key]

    override fun contains(key: String): Boolean = factories.containsKey(key)

    override fun keys(): Set<String> = factories.keys.toSet()
}

/**
 * PlaceholderAPI splits its parameters on '_', so a key containing one cannot be
 * addressed in a placeholder. Rejecting it at registration beats shipping
 * example keys shaped to dodge the problem.
 */
internal val KEY_PATTERN = Regex("[a-z0-9-]+")

internal fun isValidKey(key: String) = KEY_PATTERN.matches(key)
