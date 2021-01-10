package com.github.jonasrutishauser.transactional.event.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class PublishedEventsTest {

    @Test
    void proxyConstructor() {
        assertNotNull(new PublishedEvents());
    }

}
