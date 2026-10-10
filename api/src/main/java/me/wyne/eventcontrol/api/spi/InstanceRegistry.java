package me.wyne.eventcontrol.api.spi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Holds one shared object per key, where the object carries its own key.
 * Registering twice under the same key is refused with a logged warning.
 */
public interface InstanceRegistry<T> {

    void register(@NotNull T value);

    @Nullable T get(@NotNull String key);

    boolean contains(@NotNull String key);

    /** Every registered key, including additions from other plugins. */
    @NotNull Set<String> keys();

}
