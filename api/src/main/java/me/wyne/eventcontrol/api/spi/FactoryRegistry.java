package me.wyne.eventcontrol.api.spi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Holds one factory per key, so each config entry naming the key gets its own
 * object built from that entry.
 */
public interface FactoryRegistry<T> {

    void register(@NotNull String key, @NotNull ConfigFactory<T> factory);

    @Nullable ConfigFactory<T> get(@NotNull String key);

    boolean contains(@NotNull String key);

    @NotNull Set<String> keys();

}
