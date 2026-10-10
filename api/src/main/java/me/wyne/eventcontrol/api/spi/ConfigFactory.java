package me.wyne.eventcontrol.api.spi;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

/**
 * Builds one object per config key that names it.
 *
 * <p>The section passed is the <em>enclosing</em> section, not the value at the
 * key. That lets one factory accept {@code my-key: 5}, {@code my-key: [a, b]}
 * or a {@code my-key:} block, and lets it read sibling keys.
 */
public interface ConfigFactory<T> {

    @NotNull T create(@NotNull String key, @NotNull ConfigurationSection config);

}
