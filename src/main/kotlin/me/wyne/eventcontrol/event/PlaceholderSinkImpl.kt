package me.wyne.eventcontrol.event

import me.wyne.eventcontrol.api.text.PlaceholderSink
import me.wyne.wutils.i18n.I18n
import me.wyne.wutils.i18n.language.replacement.ComponentPlaceholder
import me.wyne.wutils.i18n.language.replacement.ComponentReplacement
import me.wyne.wutils.i18n.language.replacement.Placeholder
import me.wyne.wutils.i18n.language.replacement.TextReplacement
import net.kyori.adventure.text.Component

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
    private val components = linkedMapOf<String, Component>()

    override fun text(key: String, value: String) {
        texts[key] = value
    }

    /** Parsed with whichever markup flavour the config selected, never a fixed one. */
    override fun component(key: String, markup: String) {
        components[key] = I18n.global!!.component().fromString(markup)
    }

    /**
     * For values the engine has already resolved to a component, so they are not
     * serialized and reparsed just to be spliced in.
     */
    fun component(key: String, value: Component) {
        components[key] = value
    }

    /** Substituted before the message's markup is parsed. */
    fun textReplacements(): Array<TextReplacement> =
        texts.map { (key, value) -> Placeholder.replace(key, value) }.toTypedArray()

    /**
     * Spliced into the built message and matched literally, so a value cannot
     * change the markup around it.
     */
    fun componentReplacements(): Array<ComponentReplacement> =
        components.map { (key, value) -> ComponentPlaceholder.replace(key, value) }.toTypedArray()
}
