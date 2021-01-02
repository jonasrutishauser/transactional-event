package com.github.jonasrutishauser.transactional.event.api.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AbstractHandlerTest {

    @Test
    void testHandle() {
        AbstractHandler<Integer> testee = new AbstractHandler<Integer>() {
            @Override
            protected void handle(Integer event) {
                assertEquals(Integer.valueOf(42), event);
            }
        };
        testee.setDeserializer(value -> {
            assertEquals("serialized", value);
            return Integer.valueOf(42);
        });
        
        testee.handle("serialized");
    }

}
