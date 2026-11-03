package com.polemon.github_manager.model.dto;

import lombok.*;

import java.time.Duration;
import java.time.Instant;

/**
 * Transfer object for the GitHub API rate limit, used in the view layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RateLimitDTO {

    /**
     * Number of API calls remaining in the current rate-limit window.
     */
    private int remaining;

    /**
     * Total number of API calls allowed per rate-limit window.
     */
    private int limit;

    /**
     * Instant at which the rate-limit window resets.
     */
    private Instant resetAt;

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
