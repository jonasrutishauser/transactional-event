package com.github.jonasrutishauser.transactional.event.api;

public interface EventTypeResolver {

    String resolve(Class<?> type);

}
