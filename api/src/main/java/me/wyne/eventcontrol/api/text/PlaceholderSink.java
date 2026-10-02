package me.wyne.eventcontrol.api.text;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Collects the placeholders available to one message or screen.
 *
 * <p>This is what replaces a fixed array of replacements: the engine
 * contributes the generic values, and whatever knows more adds to the same
 * sink, so an event can expose facts nothing else could have known about.
 */
public interface PlaceholderSink {

    /**
     * A value spliced in before markup is parsed, so markup inside it renders.
     * For trusted, plugin-controlled values only.
     */
    void text(@NotNull String key, @NotNull String value);

    /**
     * A value spliced in after markup is parsed, so markup inside it stays
     * inert. Use this for anything player-derived.
     */
    void component(@NotNull String key, @NotNull String miniMessage);

    /**
     * Expands one marker line into a run of lines. The marker's own line is
     * replaced; an empty list removes it.
     */
    void lines(@NotNull String key, @NotNull List<String> miniMessages);
}
