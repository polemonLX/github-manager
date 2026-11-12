package com.polemon.github_manager.model.dto;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitDTOTest {

    @Test
    void derivesThePercentLeftAndTheMinutesToTheReset() {
        RateLimitDTO rateLimit = new RateLimitDTO(2500, 5000, Instant.now().plus(Duration.ofMinutes(10)));

        assertEquals(50, rateLimit.getRemainingPercent());
        assertTrue(rateLimit.getMinutesUntilReset() >= 9);
        assertEquals(0, new RateLimitDTO(0, 0, Instant.now().minusSeconds(60)).getRemainingPercent());
        assertEquals(0, new RateLimitDTO(0, 0, Instant.now().minusSeconds(60)).getMinutesUntilReset());
    }

}
