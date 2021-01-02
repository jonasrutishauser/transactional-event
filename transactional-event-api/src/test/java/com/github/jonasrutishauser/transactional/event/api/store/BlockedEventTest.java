package com.github.jonasrutishauser.transactional.event.api.store;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class BlockedEventTest {

    private final BlockedEvent testee = new BlockedEvent("id", "type", "payload", LocalDateTime.of(2021, 1, 1, 12, 42));

    @Test
    void testGetEventId() {
        assertEquals("id", testee.getEventId());
    }

    @Test
    void testGetEventType() {
        assertEquals("type", testee.getEventType());
    }

    @Test
    void testGetPayload() {
        assertEquals("payload", testee.getPayload());
    }

    @Test
    void testGetPublishedAt() {
        assertEquals(LocalDateTime.of(2021, 1, 1, 12, 42), testee.getPublishedAt());
    }

}
