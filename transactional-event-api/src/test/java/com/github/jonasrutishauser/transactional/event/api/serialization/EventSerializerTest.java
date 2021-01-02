package com.github.jonasrutishauser.transactional.event.api.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class EventSerializerTest {

    @Test
    void testGetTypeSimpleClass() {
        assertEquals(Integer.class, new EventSerializer<Integer>() {
            @Override
            public String serialize(Integer event) {
                return fail("should not be called");
            }
        }.getType());
    }

    @Test
    void testGetTypeGeneric() {
        assertEquals(List.class, new EventSerializer<List<String>>() {
            @Override
            public String serialize(List<String> event) {
                return fail("should not be called");
            }
        }.getType());
    }

    @Test
    void testGetTypeLambda() {
        assertThrows(IllegalStateException.class,
                ((EventSerializer<Boolean>) event -> fail("should not be called"))::getType,
                "Class does not implement EventSerializer directly");
    }

    @Test
    void testGetTypeGenericClass() {
        assertThrows(IllegalStateException.class, new TestSerializer<Boolean>()::getType,
                "Class does not implement EventSerializer directly");
    }

    private static class TestSerializer<T> implements Function<String, String>, EventSerializer<T> {
        @Override
        public String serialize(T event) {
            return fail("should not be called");
        }

        @Override
        public String apply(String value) {
            return fail("should not be called");
        }
    }

}
