package com.github.jonasrutishauser.transactional.event.api.store;

public interface QueryAdapter {
    String fixLimits(String sql);

    String addSkipLocked(String sql);
}
