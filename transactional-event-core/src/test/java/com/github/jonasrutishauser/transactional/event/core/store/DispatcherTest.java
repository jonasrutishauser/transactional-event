package com.github.jonasrutishauser.transactional.event.core.store;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class DispatcherTest {

    @Test
    void proxyConstructor() {
        assertNotNull(new Dispatcher());
    }

}
