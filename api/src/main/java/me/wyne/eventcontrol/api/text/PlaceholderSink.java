package me.wyne.eventcontrol.api.text;

import org.jetbrains.annotations.NotNull;

/**
 * Collects the placeholders available to one message.
 *
 * <p>This is what replaces a fixed array of replacements: the engine contributes
 * the generic values, and whatever knows more adds to the same sink, so an event
 * can expose facts nothing else could have known about.
 *
 * <p>Values are addressed in a language file as {@code <key>}.
 */
public interface PlaceholderSink {

    /**
     * A value substituted into the message before its markup is parsed, so any
     * markup inside the value is parsed as part of the surrounding message.
     *
     * <p>Use this for plain, plugin-controlled values — a count, a key, a
     * coordinate. A value carrying markup can alter the rest of the message, so
     * it is the wrong choice for anything a player supplied.
     */
    void text(@NotNull String key, @NotNull String value);

    /**
     * A value parsed on its own with the markup flavour EventControl is
     * configured for — {@code serializer} in its config, so MiniMessage, legacy
     * or EnhancedLegacyText — and then spliced into the already-built message.
     *
     * <p>Because it is parsed separately, the value renders its own markup but
     * cannot change the markup around it.
     */
    void component(@NotNull String key, @NotNull String markup);
}
