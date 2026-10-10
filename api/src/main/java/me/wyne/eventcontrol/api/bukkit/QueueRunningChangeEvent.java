package me.wyne.eventcontrol.api.bukkit;

import me.wyne.eventcontrol.api.queue.QueueView;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a queue starts or stops ticking, whether from config on load, an
 * admin command, or shutdown.
 *
 * <p>A stop at shutdown does not reach a dependent plugin — see
 * {@link QueueEvent}. In practice {@code running=false} is observable only when
 * a queue is stopped on a running server, by command or by a reload.
 *
 * <p>A queue stopping ends whatever was running first, so that slot's
 * {@code FINISHED} {@link QueueSlotStateChangeEvent} arrives before this one. On
 * shutdown the rest of the timeline is cancelled afterwards, so those
 * {@code CANCELLED} events arrive after it.
 */
public class QueueRunningChangeEvent extends QueueEvent {

    private final boolean running;

    public QueueRunningChangeEvent(@NotNull QueueView queue, boolean running) {
        super(queue);
        this.running = running;
    }

    /** Whether the queue is now running. */
    public boolean isRunning() {
        return running;
    }

}
