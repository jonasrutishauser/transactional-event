package com.github.jonasrutishauser.transactional.event.core.serialization;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import com.github.jonasrutishauser.transactional.event.api.serialization.GenericSerialization;

@Priority(500)
@ApplicationScoped
public class JaxbSerialization implements GenericSerialization {

    private final Map<Class<?>, JAXBContext> contexts = new ConcurrentHashMap<>();

    @Override
    public boolean accepts(Class<?> type) {
        return type.getAnnotation(XmlType.class) != null || type.getAnnotation(XmlRootElement.class) != null;
    }

    @Override
    public String serialize(Object event) {
        StringWriter writer = new StringWriter();
        try {
            Marshaller marshaller = getContext(event.getClass()).createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            marshaller.marshal(getJaxbElement(event, event.getClass()), writer);
        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
        return writer.toString();
    }

    @Override
    public <T> T deserialize(String event, Class<T> type) {
        StringReader reader = new StringReader(event);
        try {
            Unmarshaller unmarshaller = getContext(type).createUnmarshaller();
            if (type.getAnnotation(XmlRootElement.class) != null) {
                return type.cast(unmarshaller.unmarshal(reader));
            }
            return unmarshaller.unmarshal(new StreamSource(reader), type).getValue();
        } catch (JAXBException | NoSuchMethodError e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> Object getJaxbElement(Object event, Class<T> type) {
        if (type.getAnnotation(XmlRootElement.class) != null) {
            return event;
        }
        return new JAXBElement<T>(new QName("event"), type, type.cast(event));
    }

    private JAXBContext getContext(Class<?> type) {
        return contexts.computeIfAbsent(type, this::newContext);
    }

    private JAXBContext newContext(Class<?> type) {
        try {
            return JAXBContext.newInstance(type);
        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
    }

}
