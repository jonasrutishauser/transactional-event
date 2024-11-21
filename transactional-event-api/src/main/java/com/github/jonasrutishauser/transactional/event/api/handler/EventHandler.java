package com.github.jonasrutishauser.transactional.event.api.handler;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

@Qualifier
@Documented
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface EventHandler {

    String eventType() default ABSTRACT_HANDLER_TYPE;

    String ABSTRACT_HANDLER_TYPE = "#abstract-handler-type";

}
