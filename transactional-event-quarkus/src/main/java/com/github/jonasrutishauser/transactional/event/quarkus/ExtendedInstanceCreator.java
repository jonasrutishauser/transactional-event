package com.github.jonasrutishauser.transactional.event.quarkus;

import java.lang.annotation.Annotation;

import com.github.jonasrutishauser.jakarta.enterprise.inject.ExtendedInstance;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.invoke.Invoker;

@SuppressWarnings("rawtypes")
public class ExtendedInstanceCreator implements SyntheticBeanCreator<ExtendedInstance> {

    public static final String PRODUCER = "produer";
    public static final String TYPE = "type";

    @Override
    @SuppressWarnings("unchecked")
    public ExtendedInstance create(Instance<Object> lookup, Parameters params) {
        BeanManager beanManager = lookup.select(BeanManager.class).get();
        InjectionPoint injectionPoint = lookup.select(InjectionPoint.class).get();
        Instance<?> instance = lookup.select(params.get(TYPE, Class.class),
                injectionPoint.getQualifiers().toArray(new Annotation[0]));
        try {
            return (ExtendedInstance) params.get(PRODUCER, Invoker.class).invoke(null,
                    new Object[] {beanManager, null, instance});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
