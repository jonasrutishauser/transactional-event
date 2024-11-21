package com.github.jonasrutishauser.transactional.event.core.handler;

import java.lang.annotation.Annotation;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;

public interface EventHandlers {

    Annotation getHandlerQualifier(EventTypeResolver typeResolver, String type);

}
