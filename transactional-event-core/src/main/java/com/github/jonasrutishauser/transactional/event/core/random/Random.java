package com.github.jonasrutishauser.transactional.event.core.random;

import java.security.SecureRandom;
import java.util.Base64;

public final class Random {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Random() {
    }

    public static String randomId() {
        byte[] buffer = new byte[20];
        SECURE_RANDOM.nextBytes(buffer);
        return ENCODER.encodeToString(buffer);
    }

}
