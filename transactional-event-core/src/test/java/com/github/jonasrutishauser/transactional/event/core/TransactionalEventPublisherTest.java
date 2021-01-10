package com.github.jonasrutishauser.transactional.event.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TransactionalEventPublisherTest {

    @Test
    void proxyConstructor() {
        assertNotNull(new TransactionalEventPublisher());
    }

}
