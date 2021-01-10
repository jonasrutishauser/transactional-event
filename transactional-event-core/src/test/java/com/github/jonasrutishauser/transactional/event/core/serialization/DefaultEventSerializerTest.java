package com.github.jonasrutishauser.transactional.event.core.serialization;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class DefaultEventSerializerTest {

    @Test
    void proxyConstructor() {
        assertNotNull(new DefaultEventSerializer());
    }

}
