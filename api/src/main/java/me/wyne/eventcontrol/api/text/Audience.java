package me.wyne.eventcontrol.api.text;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Who an announcement reaches. Parsed from an {@code audience:} value such as
 * {@code world}, {@code all}, {@code participants}, {@code radius:120} or
 * {@code permission:eventcontrol.event}.
 */
public interface Audience {

    @NotNull Collection<Player> resolve(@NotNull AudienceContext context);
}
