package me.wyne.eventcontrol.event

import me.wyne.eventcontrol.api.text.PlaceholderSink
import me.wyne.wutils.i18n.language.replacement.ComponentPlaceholder
import me.wyne.wutils.i18n.language.replacement.ComponentReplacement
import me.wyne.wutils.i18n.language.replacement.Placeholder
import me.wyne.wutils.i18n.language.replacement.TextReplacement

/**
 * Collects placeholders from the engine, from every registered contributor and
 * from the event itself, then hands them over as the two kinds of replacement
 * i18n distinguishes.
 *
 * Replacing a fixed array of replacements with a sink is what lets an event
 * expose facts nothing else could have known about.
 */
class PlaceholderSinkImpl : PlaceholderSink {

    private val texts = linkedMapOf<String, String>()
    private val components = linkedMapOf<String, String>()
    private val lineRuns = linkedMapOf<String, List<String>>()

    override fun text(key: String, value: String) {
        texts[key] = value
    }

    override fun component(key: String, miniMessage: String) {
        components[key] = miniMessage
    }

    override fun lines(key: String, miniMessages: List<String>) {
        lineRuns[key] = miniMessages.toList()
    }

    val lines: Map<String, List<String>>
        get() = lineRuns.toMap()

    /** Spliced in before markup is parsed, so a value may carry markup itself. */
    fun textReplacements(): Array<TextReplacement> =
        texts.map { (key, value) -> Placeholder.replace(key, value) }.toTypedArray()

    /**
     * Spliced in after markup is parsed and matched literally, so a
     * player-derived value can never inject markup.
     */
    fun componentReplacements(): Array<ComponentReplacement> =
        components.map { (key, value) -> ComponentPlaceholder.replace(key, value) }.toTypedArray()
}
