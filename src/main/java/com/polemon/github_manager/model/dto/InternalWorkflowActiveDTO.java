package com.polemon.github_manager.model.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * Transfer object representing the runtime state of an internal workflow run,
 * active or ended, as the dashboard shows it.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class InternalWorkflowActiveDTO {

    /** The name of the workflow being executed. */
    private String workflowName;

    /** How the run is going: {@code waiting}, {@code running}, {@code succeeded}, {@code failed} or {@code stopped}. */
    private String status;

    /** Whether the run hasn't ended yet (it can be stopped, not dismissed). */
    private boolean active;

    /** Number of stages in the workflow. */
    private int stageCount;

    /** All stages in this workflow, in execution order. */
    private List<InternalWorkflowStageActiveDTO> stages;

    /** The stages currently executing. */
    private List<InternalWorkflowStageActiveDTO> currentStages;

    /** The stages waiting to be executed. */
    private List<InternalWorkflowStageActiveDTO> nextStages;

    /** Instant the workflow execution began. */
    private Instant startedAt;

    /** When the run began, formatted for the page; {@code null} before that. */
    private String startedAtText;

    /** When the run ended, formatted for the page; {@code null} while it hasn't. */
    private String finishedAtText;

    /** Why the run failed or stopped; {@code null} otherwise. */
    private String error;

    /** Estimated total execution time in seconds. */
    private long expectedCompletionTimeSeconds;

}
