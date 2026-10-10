package me.wyne.eventcontrol.api.spi;

import me.wyne.eventcontrol.api.event.EventDefinition;
import me.wyne.eventcontrol.api.event.EventType;
import me.wyne.eventcontrol.api.event.PrepareContext;
import me.wyne.eventcontrol.api.event.PreparedEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * A base for event types that leaves only the gameplay to write.
 *
 * <p>It takes its {@link Logger} by constructor rather than reading a static,
 * which is what lets a type live in a library or be unit-tested at all.
 *
 * <p>The common case is {@link #prepareBlocking}: implement it as ordinary
 * blocking code — search for a location, read a database, resolve a schematic —
 * and this class runs it off the main thread, wires cancellation to it, and
 * releases a preparation that finished after the slot was already abandoned.
 */
public abstract class AbstractEventType implements EventType {

    private final String key;
    private final Logger logger;

    protected AbstractEventType(@NotNull String key, @NotNull Logger logger) {
        this.key = key;
        this.logger = logger;
    }

    @Override
    public final @NotNull String getKey() {
        return key;
    }

    protected final @NotNull Logger getLogger() {
        return logger;
    }

    @Override
    public @NotNull EventDefinition readDefinition(@NotNull String definitionKey, @NotNull ConfigurationSection config) {
        return new SimpleEventDefinition(definitionKey, this, config);
    }

    @Override
    public @NotNull CompletableFuture<PreparedEvent> prepare(@NotNull EventDefinition definition,
                                                             @NotNull PrepareContext context) {
        return CompletableFuture
                .supplyAsync(() -> {
                    if (context.isCancelled())
                        throw new CancellationException("Slot cancelled before preparation started");

                    PreparedEvent prepared = prepareBlocking(definition, context);

                    // The slot can be abandoned while the search is still running,
                    // so hand nothing back that would never be released.
                    if (context.isCancelled()) {
                        prepared.release();
                        throw new CancellationException("Slot cancelled during preparation");
                    }
                    return prepared;
                }, context.getAsyncExecutor());
    }

    /**
     * Resolves everything about the next run of {@code definition}. Runs off the
     * main thread, so anything touching the Bukkit API must be scheduled back —
     * see {@link PrepareContext#getAsyncExecutor()}.
     *
     * <p>Poll {@link PrepareContext#isCancelled()} inside any long loop and bail
     * out; whatever has been allocated by then should be undone before throwing.
     */
    protected abstract @NotNull PreparedEvent prepareBlocking(@NotNull EventDefinition definition,
                                                              @NotNull PrepareContext context);

}
