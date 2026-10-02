package me.wyne.eventcontrol.api.event;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * An immutable, configured event read out of {@code event/<key>.yml}.
 * Implementations are supplied by the owning {@link EventType} and may carry
 * any additional configured facts that type needs.
 */
public interface EventDefinition {

    @NotNull String getKey();

    @NotNull EventType getType();

    /**
     * The i18n key of this event's display name. Resolved against the language
     * files of whichever plugin owns the key, so a provider ships its own wording.
     */
    @NotNull String getDisplayKey();

    /** How long the event runs once started. */
    @NotNull Duration getDuration();

    /** Minimum time after this event ends before it may be selected again. */
    @NotNull Duration getCooldown();

    /**
     * Relative weight for weighted selection. A weight of zero makes the event
     * selectable only by an explicit request or a poll.
     */
    int getWeight();
}
