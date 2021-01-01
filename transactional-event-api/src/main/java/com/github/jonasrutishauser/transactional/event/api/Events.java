package com.github.jonasrutishauser.transactional.event.api;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

@Qualifier
@Documented
@Target({TYPE, FIELD, PARAMETER, METHOD})
@Retention(RUNTIME)
public @interface Events {

}
