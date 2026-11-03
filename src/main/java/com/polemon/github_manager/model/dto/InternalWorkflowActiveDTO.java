package com.polemon.github_manager.model.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * Transfer object representing the runtime state of an actively executing internal workflow.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InternalWorkflowActiveDTO {

    /** The name of the workflow being executed. */
    private String workflowName;

    /** All stages in this workflow, in execution order. */
    private List<InternalWorkflowStageActiveDTO> stages;

    /** The stages currently executing. */
    private List<InternalWorkflowStageActiveDTO> currentStages;

    /** The stages waiting to be executed. */
    private List<InternalWorkflowStageActiveDTO> nextStages;

    /** Instant the workflow execution began. */
    private Instant startedAt;

    /** Estimated total execution time in seconds. */
    private long expectedCompletionTimeSeconds;

}
