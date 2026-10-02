package me.wyne.eventcontrol.api.text;

import me.wyne.eventcontrol.api.event.PreparedEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A source of placeholders for every event message and screen.
 *
 * <p>Register one to add values across all events; for values specific to one
 * kind of event, override
 * {@link PreparedEvent#contributePlaceholders(PlaceholderSink)} instead.
 */
public interface EventPlaceholders {

    void contribute(@NotNull PlaceholderSink sink, @NotNull PreparedEvent event, @Nullable Player viewer);
}
