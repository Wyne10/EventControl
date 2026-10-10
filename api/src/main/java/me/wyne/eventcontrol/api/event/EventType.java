package me.wyne.eventcontrol.api.event;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * A kind of event, registered once by the plugin that provides it.
 *
 * <p>Register an instance while your plugin enables:
 * <pre>{@code
 * EventControlApi.getRegistries().eventTypes().register(new MeadowEventType());
 * }</pre>
 */
public interface EventType {

    @NotNull String getKey();

    /**
     * Reads one configured event. The section handed over is the enclosing
     * section, so a type may read sibling keys.
     */
    @NotNull EventDefinition readDefinition(@NotNull String key, @NotNull ConfigurationSection config);

    /**
     * Resolves everything about the next run of {@code definition} without
     * making it visible in the world yet: the location, the chosen variant, the
     * reward preview. Called {@code prepare-lead} before the event is due to
     * start, so it may be slow and may run off the main thread.
     *
     * <p>A future completed exceptionally fails the slot, which is logged and
     * replanned. Returning a {@link PreparedEvent} hands ownership of anything
     * allocated to the engine, which will call {@link PreparedEvent#release()}
     * if the event never starts.
     */
    @NotNull CompletableFuture<PreparedEvent> prepare(@NotNull EventDefinition definition,
                                                      @NotNull PrepareContext context);

}
