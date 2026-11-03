package com.polemon.github_manager.model.dto;

import lombok.*;

import java.time.Instant;

/**
 * Transfer object representing the runtime state of a single stage within an
 * actively executing internal workflow.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InternalWorkflowStageActiveDTO {

    /** Zero-based position of this stage in the workflow. */
    private int index;

    /** Human-readable title of the stage shown in the UI. */
    private String title;

    /** Additional details describing the stage's action, shown in the UI. */
    private String details;

    /** Current execution status of the stage, e.g. {@code pending}, {@code running}, {@code done}. */
    private String status;

    /** Instant this stage began executing. */
    private Instant startedAt;

    /** Estimated execution time of this stage in seconds. */
    private long expectedDurationSeconds;

}
