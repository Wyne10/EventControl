package me.wyne.eventcontrol.api.spi;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses duration strings such as {@code 30s}, {@code 5m}, {@code 1h30m} or
 * {@code 2d}. A bare number is read as seconds.
 *
 * <p>This exists so the published API carries no library type for durations.
 */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([a-z]*)");

    private Durations() {
    }

    public static @NotNull Duration parse(@NotNull String value) {
        String normalized = value.trim().toLowerCase();
        if (normalized.isEmpty())
            throw new IllegalArgumentException("Empty duration");

        Matcher matcher = PART.matcher(normalized);
        Duration total = Duration.ZERO;
        int matchedTo = 0;
        boolean any = false;

        while (matcher.find()) {
            if (matcher.start() != matchedTo)
                throw new IllegalArgumentException("Malformed duration '" + value + "'");
            matchedTo = matcher.end();
            any = true;
            total = total.plus(unit(matcher.group(2), value).multipliedBy(Long.parseLong(matcher.group(1))));
        }

        if (!any || matchedTo != normalized.length())
            throw new IllegalArgumentException("Malformed duration '" + value + "'");
        return total;
    }

    public static @NotNull Duration read(@NotNull ConfigurationSection config, @NotNull String path,
                                         @NotNull Duration fallback) {
        String value = config.getString(path);
        return value == null ? fallback : parse(value);
    }

    private static Duration unit(String suffix, String value) {
        switch (suffix) {
            case "":
            case "s":
                return Duration.ofSeconds(1);
            case "ms":
                return Duration.ofMillis(1);
            case "m":
                return Duration.ofMinutes(1);
            case "h":
                return Duration.ofHours(1);
            case "d":
                return Duration.ofDays(1);
            default:
                throw new IllegalArgumentException("Unknown duration unit '" + suffix + "' in '" + value + "'");
        }
    }

}
