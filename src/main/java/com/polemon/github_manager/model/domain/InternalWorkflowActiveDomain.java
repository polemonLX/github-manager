package com.polemon.github_manager.model.domain;

import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime state of an internal workflow run: its stages, which of them run
 * now and which wait, and how the run went. One worker thread changes it
 * while the dashboard reads it, so the lists are copy-on-write and the
 * scalar fields volatile.
 *
 * <p>Stages run in order, grouped like animation effects in a slideshow: a
 * {@link InternalWorkflowStageActiveDomain.RunMode#WITH_PREVIOUS} stage joins
 * the group of the stage before it and runs in parallel with it; any other
 * stage waits until the whole previous group has finished and opens a new
 * group.
 */
@ToString
public class InternalWorkflowActiveDomain {

    /**
     * The name of the workflow being executed.
     */
    @Getter
    private final String workflowName;

    /**
     * All stages in this workflow, in execution order.
     */
    @Getter
    private final List<InternalWorkflowStageActiveDomain> stages;

    /**
     * The stages of the group running now.
     */
    private final List<InternalWorkflowStageActiveDomain> currentStages = new CopyOnWriteArrayList<>();

    /**
     * The stages not taken yet, in order.
     */
    private final List<InternalWorkflowStageActiveDomain> nextStages = new CopyOnWriteArrayList<>();

    /**
     * How the run is going.
     */
    @Getter
    private volatile WorkflowStatus status = WorkflowStatus.WAITING;

    /**
     * Instant the workflow execution began.
     */
    @Getter
    private volatile Instant startedAt;

    /**
     * Instant the run ended; {@code null} while it hasn't.
     */
    @Getter
    private volatile Instant finishedAt;

    /**
     * Why the run failed or stopped; {@code null} otherwise.
     */
    @Getter
    private volatile String error;

    /**
     * Estimated total execution time in seconds (not estimated yet: 0).
     */
    @Getter
    private long expectedCompletionTimeSeconds;

    public InternalWorkflowActiveDomain(String workflowName, List<InternalWorkflowStageActiveDomain> stages) {
        this.workflowName = workflowName;
        this.stages = List.copyOf(stages);
        this.nextStages.addAll(stages);
    }

    /**
     * The stages of the group running now.
     *
     * @return a snapshot, in order
     */
    public List<InternalWorkflowStageActiveDomain> getCurrentStages() {
        return List.copyOf(currentStages);
    }

    /**
     * The stages not taken yet.
     *
     * @return a snapshot, in order
     */
    public List<InternalWorkflowStageActiveDomain> getNextStages() {
        return List.copyOf(nextStages);
    }

    /**
     * Takes the next stage of the current group. Call it until it answers
     * {@code null} to get the whole group, run the group, then
     * {@link #clearCurrentStages()} before asking for the next group.
     *
     * @return the next stage, moved to the current group; {@code null} when no
     *         stage is left, or the next one must wait for the current group
     *         (it isn't {@code WITH_PREVIOUS})
     */
    public synchronized InternalWorkflowStageActiveDomain nextStage() {
        if (nextStages.isEmpty())
            return null;
        InternalWorkflowStageActiveDomain next = nextStages.getFirst();
        if (!currentStages.isEmpty() && next.getRunMode() != InternalWorkflowStageActiveDomain.RunMode.WITH_PREVIOUS)
            return null;
        nextStages.removeFirst();
        currentStages.add(next);
        return next;
    }

    /**
     * Ends the current group, once all its stages have finished.
     */
    public synchronized void clearCurrentStages() {
        currentStages.clear();
    }

    /**
     * Marks the run as started.
     *
     * @param now the current instant
     */
    public void start(Instant now) {
        this.startedAt = now;
        this.status = WorkflowStatus.RUNNING;
    }

    /**
     * Ends the run.
     *
     * @param endStatus how it ended
     * @param reason    why it failed or stopped; {@code null} if it succeeded
     * @param now       the current instant
     */
    public void finish(WorkflowStatus endStatus, String reason, Instant now) {
        this.error = reason;
        this.finishedAt = now;
        this.status = endStatus;
    }

    /**
     * Tells whether the run hasn't ended yet.
     *
     * @return {@code true} while it waits to start or runs
     */
    public boolean isActive() {
        return status == WorkflowStatus.WAITING || status == WorkflowStatus.RUNNING;
    }

    /**
     * How an internal workflow run is going.
     */
    public enum WorkflowStatus {
        /**
         * Accepted, not started yet.
         */
        WAITING,
        /**
         * Running its stages.
         */
        RUNNING,
        /**
         * Every stage succeeded.
         */
        SUCCEEDED,
        /**
         * A stage failed; the stages after it didn't run.
         */
        FAILED,
        /**
         * Stopped by the user.
         */
        STOPPED
    }

}
