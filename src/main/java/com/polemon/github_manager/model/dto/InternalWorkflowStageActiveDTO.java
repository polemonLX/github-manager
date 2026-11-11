package com.polemon.github_manager.model.dto;

import lombok.*;

import java.time.Instant;

/**
 * Transfer object representing the runtime state of a single stage within an
 * internal workflow run.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class InternalWorkflowStageActiveDTO {

    /** Zero-based position of this stage in the workflow. */
    private int index;

    /** Human-readable title of the stage shown in the UI. */
    private String title;

    /** Additional details describing the stage's action, shown in the UI. */
    private String details;

    /** Where the stage is: {@code waiting}, {@code running}, {@code succeeded}, {@code failed} or {@code stopped}. */
    private String status;

    /** Whether the stage runs together with the one before it ({@code WITH_PREVIOUS}). */
    private boolean parallel;

    /** For a pipeline stage, the URL of its run on GitHub once found; {@code null} otherwise. */
    private String runUrl;

    /** Why the stage failed; {@code null} unless it did. */
    private String error;

    /** Instant this stage began executing. */
    private Instant startedAt;

    /** Estimated execution time of this stage in seconds. */
    private long expectedDurationSeconds;

}
