package me.wyne.eventcontrol.api.text;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * What an {@link Audience} has to work with. Available both before an event
 * starts — for the early announcement — and while it runs, so the same
 * audience value means the same thing at every point in the schedule.
 */
public interface AudienceContext {

    @NotNull World getWorld();

    /** The event's place, when it has one. */
    @Nullable Location getLocation();

    /** Empty before the event starts. */
    @NotNull Collection<Player> getParticipants();
}
