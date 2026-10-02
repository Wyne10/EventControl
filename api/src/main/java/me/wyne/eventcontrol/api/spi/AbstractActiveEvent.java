package me.wyne.eventcontrol.api.spi;

import me.wyne.eventcontrol.api.event.ActiveEvent;
import me.wyne.eventcontrol.api.event.EventContext;
import me.wyne.eventcontrol.api.event.PreparedEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks the running flag and the start instant, and makes sure the end path
 * runs exactly once however the event ends.
 */
public abstract class AbstractActiveEvent implements ActiveEvent {

    private final PreparedEvent prepared;
    private final EventContext context;
    private final Instant startedAt = Instant.now();
    private final AtomicBoolean ended = new AtomicBoolean();

    protected AbstractActiveEvent(@NotNull PreparedEvent prepared, @NotNull EventContext context) {
        this.prepared = prepared;
        this.context = context;
    }

    @Override
    public final @NotNull PreparedEvent getPrepared() {
        return prepared;
    }

    @Override
    public final @NotNull Instant getStartedAt() {
        return startedAt;
    }

    @Override
    public final boolean isRunning() {
        return !ended.get();
    }

    @Override
    public final void onEnd() {
        if (ended.compareAndSet(false, true))
            onEnded();
    }

    protected final @NotNull EventContext getContext() {
        return context;
    }

    /** Ends the event now instead of waiting out its duration. */
    protected final void endNow() {
        context.endNow();
    }

    /**
     * Work that must happen before the engine closes everything bound through
     * {@link EventContext#bind}. Called exactly once.
     */
    protected void onEnded() {
    }
}
