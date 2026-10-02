package me.wyne.eventcontrol.api.event;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * A running event. The engine owns its lifetime; an implementation only holds
 * the runtime state its gameplay needs.
 */
public interface ActiveEvent {

    @NotNull PreparedEvent getPrepared();

    @NotNull Instant getStartedAt();

    boolean isRunning();

    /**
     * Called once when the event ends, for any reason. Anything bound through
     * {@link EventContext#bind} is closed by the engine afterwards, so this is
     * only for work that must happen before teardown.
     */
    default void onEnd() {
    }
}
