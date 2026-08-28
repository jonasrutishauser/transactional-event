package com.github.jonasrutishauser.transactional.event.quarkus.deployment.it;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class RequestContextProbe {

    private boolean marked;

    public void mark() {
        marked = true;
    }

    public boolean isMarked() {
        return marked;
    }
}
