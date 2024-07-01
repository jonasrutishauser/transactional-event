package com.github.jonasrutishauser.transactional.event.core.handler;

import java.util.Optional;

import com.github.jonasrutishauser.transactional.event.api.EventTypeResolver;
import com.github.jonasrutishauser.transactional.event.api.handler.Handler;

public interface EventHandlers {

    Optional<Class<? extends Handler>> getHandlerClassWithImplicitType(EventTypeResolver typeResolver, String type);

}
