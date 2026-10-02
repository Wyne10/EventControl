package me.wyne.eventcontrol.api;

import me.wyne.eventcontrol.api.event.EventDefinition;
import me.wyne.eventcontrol.api.queue.QueueView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Reading EventControl's state needs no registration and no timing care — just
 * {@code depend: [EventControl]}.
 */
public interface EventControlProvider {

    @NotNull Collection<QueueView> getQueues();

    @Nullable QueueView getQueue(@NotNull String queueKey);

    @Nullable EventDefinition getDefinition(@NotNull String definitionKey);

    @NotNull Collection<EventDefinition> getDefinitions();
}
