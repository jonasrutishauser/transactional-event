package com.github.jonasrutishauser.transactional.event.core.cdi;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Qualifier
@Retention(RUNTIME)
@Target(TYPE)
public @interface TypedEventHandler {

    Class<?> value();

    public static final class Literal extends AnnotationLiteral<TypedEventHandler> implements TypedEventHandler {
        private static final long serialVersionUID = 1L;

        private final Class<?> value;

        public Literal(Class<?> value) {
            this.value = value;
        }

        public static Literal of(Class<?> value) {
            return new Literal(value);
        }

        @Override
        public Class<?> value() {
            return value;
        }
    }
}
