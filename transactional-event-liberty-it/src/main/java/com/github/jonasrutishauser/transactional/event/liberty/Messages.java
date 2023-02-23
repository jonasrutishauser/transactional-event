package com.github.jonasrutishauser.transactional.event.liberty;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Messages {

    private final Set<String> items = new CopyOnWriteArraySet<>();
    private final Set<String> failures = new HashSet<>();

    protected void add(String message) {
        items.add(message);
    }

    public Collection<String> get() {
        return items;
    }

    protected boolean addFailure(String message) {
        if (!failures.add(message)) {
            failures.remove(message);
            return false;
        }
        return true;
    }
}
