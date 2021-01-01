package com.github.jonasrutishauser.transactional.event.core.openejb;

import org.apache.openejb.testing.ApplicationComposers;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

public class ApplicationComposerExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        ClosableApplicationComposers delegate = new ClosableApplicationComposers(context.getRequiredTestClass());
        context.getStore(Namespace.create(getClass(), context.getRequiredTestMethod()))
                .put(ClosableApplicationComposers.class, delegate);
        delegate.before(context.getRequiredTestInstance());
    }

    private static class ClosableApplicationComposers extends ApplicationComposers implements CloseableResource {
        public ClosableApplicationComposers(Class<?> klass) {
            super(klass);
        }

        @Override
        public void close() throws Throwable {
            after();
        }
    }

}
