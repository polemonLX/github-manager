package com.polemon.github_manager.model.domain;

import lombok.*;

import java.time.Instant;

/**
 * Runtime state of a single stage within an actively executing internal workflow.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InternalWorkflowStageActiveDomain {

    /**
     * Zero-based position of this stage in the workflow.
     */
    private int index;

    /**
     * The repository this stage acts on.
     */
    private RepositoryDomain repository;

    /**
     * Whether this stage performs a commit or triggers a pipeline.
     */
    private StageTask task;

    /**
     * For a commit stage, the kind of change to make; {@code null} otherwise.
     */
    private CommitOperation commitOperation;

    /**
     * The branch to act on.
     */
    private String targetBranch;

    /**
     * For a commit stage, the path of the file to change; {@code null} otherwise.
     */
    private String commitFilePath;

    /**
     * For a find-and-replace commit, the regular expression to match; {@code null} otherwise.
     */
    private String commitFindRegex;

    /**
     * For a set-content commit, the new file content; for a find-and-replace
     * commit, the replacement text. {@code null} otherwise.
     */
    private String commitContent;

    /**
     * For a commit stage, the commit message; {@code null} otherwise.
     */
    private String commitMessage;

    /**
     * For a pipeline stage, the URL of the GitHub Actions workflow to run; {@code null} otherwise.
     */
    private String workflowUrl;

    /**
     * Whether this stage runs after the previous stage finishes or together with it.
     */
    private RunMode runMode;

    /**
     * For a Maven version bump, which version component to increment; supplied
     * when the workflow is started. {@code null} otherwise (defaults to patch).
     */
    private VersionPart versionPart;

    /**
     * Instant this stage began executing.
     */
    private Instant startedAt;

    /**
     * Estimated execution time of this stage in seconds.
     */
    private long expectedDurationSeconds;

    /**
     * Where the stage is in the run; the worker writes it and the dashboard
     * reads it, so it is volatile.
     */
    private volatile StageStatus status = StageStatus.WAITING;

    /**
     * Instant the stage ended (succeeded, failed or stopped); {@code null}
     * before that.
     */
    private volatile Instant finishedAt;

    /**
     * For a pipeline stage, the GitHub run its dispatch created, once found.
     */
    private volatile Long runId;

    /**
     * For a pipeline stage, the URL of that run on GitHub, once found.
     */
    private volatile String runUrl;

    /**
     * Why the stage failed; {@code null} unless it did.
     */
    private volatile String error;

    /**
     * Ends the stage.
     *
     * @param endStatus how it ended
     * @param reason    why, when it failed; {@code null} otherwise
     * @param now       the current instant
     */
    public void finish(StageStatus endStatus, String reason, Instant now) {
        this.error = reason;
        this.finishedAt = now;
        this.status = endStatus;
    }

    /**
     * Where a stage is in the run of its workflow.
     */
    public enum StageStatus {
        /**
         * Not started yet.
         */
        WAITING,
        /**
         * Started: a commit being made, or a pipeline running on GitHub.
         */
        RUNNING,
        /**
         * Done: the commit was made, or the pipeline run succeeded.
         */
        SUCCEEDED,
        /**
         * The commit or dispatch was refused, or the pipeline run didn't succeed.
         */
        FAILED,
        /**
         * The workflow stopped (by the user, or because another stage failed)
         * while this stage was running; GitHub may still finish its run.
         */
        STOPPED
    }

    /**
     * The kind of action a stage performs.
     */
    public enum StageTask {
        /**
         * Commits a file change to a repository.
         */
        COMMIT,
        /**
         * Triggers a GitHub Actions pipeline.
         */
        WORKFLOW
    }

    /**
     * The kind of change a commit stage makes to a file.
     */
    public enum CommitOperation {
        /**
         * Increments the version in {@code pom.xml}.
         */
        BUMP_MAVEN_VERSION,
        /**
         * Replaces text matching a regular expression.
         */
        FIND_AND_REPLACE_REGEX,
        /**
         * Overwrites a file with fixed content.
         */
        SET_FILE_CONTENT
    }

    /**
     * When a stage starts relative to the preceding stage.
     */
    public enum RunMode {
        /**
         * Starts only after the preceding stage completes.
         */
        AFTER_PREVIOUS_FINISHES,
        /**
         * Starts at the same time as the preceding stage.
         */
        WITH_PREVIOUS
    }

    /**
     * Which component of a semantic version a Maven bump increments.
     */
    public enum VersionPart {
        MAJOR,
        MINOR,
        PATCH
    }

}
