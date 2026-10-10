package me.wyne.eventcontrol.api.spi;

import me.wyne.eventcontrol.api.event.EventDefinition;
import me.wyne.eventcontrol.api.event.EventType;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Reads the four facts every event has — display key, duration, cooldown and
 * weight — so a type only parses what is its own. Extend it and add fields.
 */
public class SimpleEventDefinition implements EventDefinition {

    private final String key;
    private final EventType type;
    private final String displayKey;
    private final Duration duration;
    private final Duration cooldown;
    private final int weight;

    public SimpleEventDefinition(@NotNull String key, @NotNull EventType type, @NotNull ConfigurationSection config) {
        this.key = key;
        this.type = type;
        this.displayKey = config.getString("display", "event-" + key);
        this.duration = Durations.read(config, "duration", Duration.ofMinutes(10));
        this.cooldown = Durations.read(config, "cooldown", Duration.ZERO);
        this.weight = config.getInt("weight", 1);
    }

    @Override
    public @NotNull String getKey() {
        return key;
    }

    @Override
    public @NotNull EventType getType() {
        return type;
    }

    @Override
    public @NotNull String getDisplayKey() {
        return displayKey;
    }

    @Override
    public @NotNull Duration getDuration() {
        return duration;
    }

    @Override
    public @NotNull Duration getCooldown() {
        return cooldown;
    }

    @Override
    public int getWeight() {
        return weight;
    }

}
