package org.example.plugins;

import org.jetbrains.annotations.Nullable;

/**
 * <h1>Message Bus Event</h1>
 * An object representing a single message sent through the shared event bus. These can be sent and received at any time,
 * with the exception of during initialization, on which sent messages are dropped without being forwarded.
 */
public record MessageBusEvent(String source, String recipient, Object content) {
    /**
     * <h1>Message Bus Event</h1>
     * Creates a new message bus event.
     *
     * @param source    The source of the event. This should normally be <code>this.getClass().getSimpleName()</code>, albeit it can be anything your plugin wants. Source conflicts are not checked, and as such may lead to undefined behaviour.
     * @param recipient The intended recipient of this event, such as the name of the class that produced the recipient object. This can be made null to signify a 'broadcast' message, or a message intended for all recipients. Recipients must 'subscribe' to events if this is not null to receive them.
     * @param content   The content of the event. This can be anything, from a simple String to <code>this</code>.
     */
    public MessageBusEvent(String source, @Nullable String recipient, Object content) {
        this.source = source;
        this.recipient = recipient;
        this.content = content;
    }
}
