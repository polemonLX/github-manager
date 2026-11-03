package com.polemon.github_manager.model.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Snapshot of the GitHub API rate limit for the configured token (core resource).
 * Live data fetched from the GitHub API; never persisted.
 *
 * @param remaining Number of API calls remaining in the current rate-limit window.
 * @param limit     Total number of API calls allowed per rate-limit window.
 * @param resetAt   Instant at which the rate-limit window resets.
 */
public record RateLimitDomain(int remaining, int limit, Instant resetAt) {

    /**
     * Computes the number of API calls already consumed in the current window.
     *
     * @return the used call count ({@code limit - remaining})
     */
    public int getUsed() {
        return limit - remaining;
    }

    /**
     * Computes the percentage of the rate limit still remaining.
     *
     * @return the remaining percentage, rounded to the nearest integer (0 if {@code limit} is 0)
     */
    public int getRemainingPercent() {
        return limit == 0 ? 0 : (int) Math.round(remaining * 100.0 / limit);
    }

    /**
     * Computes the number of whole minutes until the rate-limit window resets.
     *
     * @return the minutes until reset, never negative
     */
    public long getMinutesUntilReset() {
        long minutes = Duration.between(Instant.now(), resetAt).toMinutes();
        return Math.max(minutes, 0);
    }

}
