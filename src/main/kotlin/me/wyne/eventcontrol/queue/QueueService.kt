package me.wyne.eventcontrol.queue

import com.google.inject.Inject
import com.google.inject.Singleton
import me.wyne.eventcontrol.EventControl
import me.wyne.eventcontrol.api.event.EventDefinition
import me.wyne.eventcontrol.api.queue.QueueSource
import me.wyne.eventcontrol.event.Announcer
import me.wyne.eventcontrol.event.EventDefinitionProvider
import me.wyne.eventcontrol.registry.Registries
import me.wyne.wutils.common.loadable.Loadable
import me.wyne.wutils.common.loadable.LoadableMeta
import me.wyne.wutils.common.loadable.Loader
import me.wyne.wutils.common.terminable.Terminable
import me.wyne.wutils.json.JSON
import me.wyne.wutils.json.JsonRegistry
import org.bukkit.configuration.ConfigurationSection
import java.time.Duration
import java.time.Instant

/**
 * Owns the live queues and reconciles them against the configured definitions.
 *
 * Follows the family's definition/world-instance split: [QueueDefinition] is the
 * immutable recipe, [EventQueue] the running timeline. Reload re-reads the
 * recipes and rebuilds the timelines here rather than relying on the injector,
 * which is never recreated.
 */
@Singleton
@LoadableMeta(priority = 2)
class QueueService @Inject constructor(
    private val plugin: EventControl,
    private val registries: Registries,
    private val definitions: EventDefinitionProvider,
    private val queues: QueueProvider,
    private val announcer: Announcer
) : Loadable, Terminable {

    /** Resumable timelines, keyed by queue, written on disable and on reload. */
    @JSON(path = "queues.json")
    private var persisted: MutableMap<String, MutableList<PersistedSlot>> = mutableMapOf()

    private val live = linkedMapOf<String, EventQueue>()

    init {
        Loader.global.registerLoadable(this)
        JsonRegistry.global.registerObject(this)
        plugin.bind(this)
    }

    val all: Collection<EventQueue>
        get() = live.values.toList()

    operator fun get(key: String): EventQueue? = live[key]

    override fun load(config: ConfigurationSection) {
        // Idempotent: a reload must leave exactly one tick per queue and no
        // orphaned events, so the previous generation is torn down first.
        captureState()
        closeLive()

        queues.map.values.forEach { definition ->
            runCatching { build(definition) }
                .onFailure { EventControl.logger.error("Failed building queue '{}'", definition.key, it) }
        }

        live.values.forEach { queue ->
            restore(queue)
            if (queue.definition.autoStart) queue.start()
        }
        EventControl.logger.info("Loaded {} queue(s)", live.size)
    }

    private fun build(definition: QueueDefinition) {
        // A queue whose world is missing is a config error, not a runtime state:
        // failing here keeps QueueView.getWorld() safe for every live queue.
        requireNotNull(definition.world) {
            "Queue '${definition.key}' targets world '${definition.worldName}', which is not loaded"
        }
        val factory = requireNotNull(registries.queueSources().get(definition.sourceKey)) {
            "Queue '${definition.key}' names unknown source '${definition.sourceKey}'; " +
                "registered sources: ${registries.queueSources().keys()}"
        }
        val source: QueueSource = factory.create(definition.sourceKey, definition.sourceConfig)
        val queue = EventQueue(definition, source, { candidatesOf(definition) }, announcer)
        live[definition.key] = queue
    }

    private fun candidatesOf(definition: QueueDefinition): List<EventDefinition> =
        definition.eventKeys.mapNotNull { key ->
            definitions[key].also {
                if (it == null) EventControl.logger.warn(
                    "Queue '{}' lists unknown event '{}'", definition.key, key
                )
            }
        }

    //region Persistence

    /**
     * Snapshots the timelines into the persisted field. The disable step that
     * writes JSON runs before the terminable registry closes this service, so
     * the snapshot has to be taken explicitly rather than in close().
     */
    fun captureStateForWrite() = captureState()

    private fun captureState() {
        live.values.forEach { queue ->
            persisted[queue.key] = queue.persistableSlots().map { slot ->
                PersistedSlot(
                    definitionKey = slot.definition?.key,
                    startAtEpochMilli = slot.startAt.toEpochMilli(),
                    origin = slot.origin,
                    requester = slot.requester
                )
            }.toMutableList()
        }
    }

    /**
     * Rebuilds a saved timeline. Start times that have already passed are
     * shifted forward together, so the head still gets its full lead time
     * rather than firing unprepared the moment the server is up.
     */
    private fun restore(queue: EventQueue) {
        val saved = persisted[queue.key]?.takeIf { it.isNotEmpty() } ?: return
        val now = Instant.now()
        val earliest = now.plus(queue.definition.prepareLead)
        val head = Instant.ofEpochMilli(saved.minOf { it.startAtEpochMilli })
        val shift = if (head.isBefore(earliest)) Duration.between(head, earliest) else Duration.ZERO

        val slots = saved.mapNotNull { entry ->
            val startAt = Instant.ofEpochMilli(entry.startAtEpochMilli).plus(shift)
            val slot = QueueSlotImpl(entry.origin, entry.requester, startAt)
            entry.definitionKey?.let { key ->
                val definition = definitions[key]
                if (definition == null) {
                    // The event was renamed or removed while the server was down.
                    EventControl.logger.warn(
                        "Dropping saved slot for unknown event '{}' on queue '{}'", key, queue.key
                    )
                    return@mapNotNull null
                }
                slot.assign(definition)
            }
            slot
        }
        queue.restore(slots)
        EventControl.logger.debug("Restored {} slot(s) for queue '{}'", slots.size, queue.key)
    }

    //endregion

    private fun closeLive() {
        live.values.forEach { queue ->
            runCatching { queue.close() }
                .onFailure { EventControl.logger.error("Failed closing queue '{}'", queue.key, it) }
        }
        live.clear()
    }

    override fun close() {
        captureState()
        closeLive()
    }
}
