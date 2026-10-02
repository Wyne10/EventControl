package me.wyne.eventcontrol.api;

import org.jetbrains.annotations.Nullable;

/**
 * Entry point. Both accessors return null until EventControl has enabled, so a
 * {@code softdepend} consumer should check.
 */
public final class EventControlApi {

    private static EventControlProvider provider;
    private static EventControlRegistries registries;

    private EventControlApi() {
    }

    public static @Nullable EventControlProvider getProvider() {
        return provider;
    }

    public static @Nullable EventControlRegistries getRegistries() {
        return registries;
    }

    /** Called by EventControl itself. */
    public static void install(EventControlProvider newProvider, EventControlRegistries newRegistries) {
        provider = newProvider;
        registries = newRegistries;
    }
}
