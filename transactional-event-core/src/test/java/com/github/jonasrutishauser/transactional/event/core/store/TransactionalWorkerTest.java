package com.github.jonasrutishauser.transactional.event.core.store;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TransactionalWorkerTest {

    @Test
    void proxyConstructor() {
        assertNotNull(new TransactionalWorker());
    }

}
