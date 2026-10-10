package me.wyne.eventcontrol.api;

import me.wyne.eventcontrol.api.event.EventType;
import me.wyne.eventcontrol.api.queue.QueueSource;
import me.wyne.eventcontrol.api.spi.FactoryRegistry;
import me.wyne.eventcontrol.api.spi.InstanceRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Everything a plugin can add to EventControl.
 *
 * <p>Register while your own plugin enables. The registries close one tick
 * after the server finishes enabling plugins — late enough for every
 * {@code depend: [EventControl]} plugin to have run its {@code onEnable}, and
 * before the event definitions that would use the keys are read. A registration
 * arriving later is refused with a logged warning; {@link #isOpen()} says which
 * side of that line you are on.
 *
 * <p>A reload does not reopen them, and does not need to: registered types
 * survive, only the definitions bound to them are rebuilt.
 */
public interface EventControlRegistries {

    /** Kinds of event. One object per kind, carrying its own key. */
    @NotNull InstanceRegistry<EventType> eventTypes();

    /** Ways of deciding what a queue runs next. One object per configured queue. */
    @NotNull FactoryRegistry<QueueSource> queueSources();

    /** Whether registrations are still accepted. */
    boolean isOpen();
}
