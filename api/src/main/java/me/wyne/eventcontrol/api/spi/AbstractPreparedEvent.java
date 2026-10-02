package me.wyne.eventcontrol.api.spi;

import me.wyne.eventcontrol.api.event.ActiveEvent;
import me.wyne.eventcontrol.api.event.EventContext;
import me.wyne.eventcontrol.api.event.EventDefinition;
import me.wyne.eventcontrol.api.event.PreparedEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles the bookkeeping every prepared event shares: the start and end
 * instants, and the guarantee that it either starts once or is released once,
 * never both and never twice.
 *
 * <p>That last guarantee is what the hand-written {@code isClosed} flag used to
 * do in each event plugin.
 */
public abstract class AbstractPreparedEvent implements PreparedEvent {

    private final EventDefinition definition;
    private final Instant startAt;
    private final AtomicBoolean consumed = new AtomicBoolean();

    protected AbstractPreparedEvent(@NotNull EventDefinition definition, @NotNull Instant startAt) {
        this.definition = definition;
        this.startAt = startAt;
    }

    @Override
    public final @NotNull EventDefinition getDefinition() {
        return definition;
    }

    @Override
    public final @NotNull Instant getStartAt() {
        return startAt;
    }

    @Override
    public final @NotNull Instant getEndAt() {
        return startAt.plus(definition.getDuration());
    }

    @Override
    public final @NotNull ActiveEvent start(@NotNull EventContext context) {
        if (!consumed.compareAndSet(false, true))
            throw new IllegalStateException("Prepared event '" + definition.getKey() + "' was already consumed");
        return onStart(context);
    }

    @Override
    public final void release() {
        if (consumed.compareAndSet(false, true))
            onRelease();
    }

    /** Makes the event visible in the world. */
    protected abstract @NotNull ActiveEvent onStart(@NotNull EventContext context);

    /**
     * Discards whatever preparation allocated, because the event will never
     * start. Called at most once, and never after {@link #onStart}.
     */
    protected void onRelease() {
    }
}
