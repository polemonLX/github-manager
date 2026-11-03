package com.polemon.github_manager.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

/**
 * Runtime state of an actively executing internal workflow.
 */
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InternalWorkflowActiveDomain {

    /** The name of the workflow being executed. */
    @Getter
    private String workflowName;

    /** All stages in this workflow, in execution order. */
    @Getter
    private List<InternalWorkflowStageActiveDomain> stages;

    /** The stages currently executing. */
    @Getter
    private List<InternalWorkflowStageActiveDomain> currentStages;

    /** The stages waiting to be executed. */
    @Getter
    private List<InternalWorkflowStageActiveDomain> nextStages;

    /** Instant the workflow execution began. */
    @Getter
    private Instant startedAt;

    /** Estimated total execution time in seconds. */
    @Getter
    private long expectedCompletionTimeSeconds;

    public InternalWorkflowActiveDomain(String workflowName, List<InternalWorkflowStageActiveDomain> stages) {
        this.workflowName = workflowName;
        this.stages = stages;

        this.currentStages = new LinkedList<>();
        this.nextStages = new LinkedList<>(stages);
    }

    /**
     * Returns the next stage to execute, or {@code null} if none remain.
     *
     * @return the next stage, or {@code null}
     */
    public InternalWorkflowStageActiveDomain nextStage() {
        return null;
    }

    /** Clears the list of currently executing stages. */
    public void clearCurrentStages() {

    }

}
