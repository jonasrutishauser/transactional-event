package com.github.jonasrutishauser.transactional.event.core.cdi;

import com.github.jonasrutishauser.transactional.event.api.handler.EventHandler;

import jakarta.enterprise.util.AnnotationLiteral;

public class EventHandlerLiteral extends AnnotationLiteral<EventHandler> implements EventHandler {
    private static final long serialVersionUID = 1L;

    private final String eventType;

    private EventHandlerLiteral(String eventType) {
        this.eventType = eventType;
    }

    public static EventHandlerLiteral of(String eventType) {
        return new EventHandlerLiteral(eventType);
    }

    @Override
    public String eventType() {
        return eventType;
    }
}
