package com.polemon.github_manager.model.dto.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitAPITest {

    @Test
    void derivesTheUsedCallsThePercentLeftAndTheMinutesToTheReset() {
        RateLimitAPI rateLimit = new RateLimitAPI(4999, 5000, Instant.now().plus(Duration.ofMinutes(60)));

        assertEquals(1, rateLimit.getUsed());
        assertEquals(100, rateLimit.getRemainingPercent());
        assertTrue(rateLimit.getMinutesUntilReset() >= 59);
        assertEquals(0, new RateLimitAPI(0, 0, Instant.now().minusSeconds(60)).getRemainingPercent());
        assertEquals(0, new RateLimitAPI(0, 0, Instant.now().minusSeconds(60)).getMinutesUntilReset());
    }

}
