package com.github.jonasrutishauser.transactional.event.quarkus;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.invoke.Invoker;

public class LifecycleObserver implements SyntheticObserver<Object> {

    public static final String INVOKER = "invoker";
    public static final String TYPE = "type";

    @Override
    @SuppressWarnings("unchecked")
    public void observe(EventContext<Object> event, Parameters params) throws Exception {
        Object instance = null;
        Class<?> type = params.get(TYPE, Class.class);
        if (type != null) {
            ArcContainer container = Arc.container();
            InstanceHandle<?> handle = container.instance(type);
            instance = container.getActiveContext(handle.getBean().getScope()).get(handle.getBean());
            if (instance == null) {
                return;
            }
        }
        params.get(INVOKER, Invoker.class).invoke(instance, new Object[] {event.getEvent()});
    }

}
