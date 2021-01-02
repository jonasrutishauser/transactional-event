package com.github.jonasrutishauser.transactional.event.api.serialization;

import static org.junit.jupiter.api.Assertions.*;

import javax.annotation.Priority;

import org.junit.jupiter.api.Test;

class GenericSerializationTest {

    @Test
    void testPriorityWithoutPriority() {
        int priority = new WithoutPriority().priority();

        assertEquals(0, priority);
    }

    @Test
    void testPriorityWithPriority() {
        int priority = new WithPriority().priority();

        assertEquals(42, priority);
    }

    private static class WithoutPriority implements GenericSerialization {
        @Override
        public boolean accepts(Class<?> type) {
            return fail("should not be called");
        }

        @Override
        public String serialize(Object event) {
            return fail("should not be called");
        }

        @Override
        public <T> T deserialize(String event, Class<T> type) {
            return fail("should not be called");
        }
    }

    @Priority(42)
    private static class WithPriority extends WithoutPriority {}

}
