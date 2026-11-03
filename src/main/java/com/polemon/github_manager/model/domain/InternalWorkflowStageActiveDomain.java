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
