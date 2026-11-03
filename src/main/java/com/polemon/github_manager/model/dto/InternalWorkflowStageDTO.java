package com.polemon.github_manager.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * A single stage of an {@link InternalWorkflowDTO}. A stage either commits a change
 * to a repository or triggers one of its GitHub Actions pipelines. Task-specific
 * fields are only populated for the relevant {@link StageTask}; unused fields are
 * omitted from the persisted JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalWorkflowStageDTO {

    /**
     * The repository this stage acts on.
     */
    private RepositoryDTO repository;

    /**
     * Whether this stage performs a commit or triggers a pipeline.
     */
    private StageTask task;

    /**
     * For a commit task, the kind of change to make; {@code null} otherwise.
     */
    private CommitOperation commitOperation;

    /**
     * The branch to act on, or {@code null} to decide when the workflow runs.
     * Applies to both commit and pipeline tasks.
     */
    private String targetBranch;

    /**
     * For a commit task, the path of the file to change; {@code null} otherwise.
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
     * For a commit task, the commit message; {@code null} otherwise.
     */
    private String commitMessage;

    /**
     * For a Maven version bump, which version component to increment; {@code null} otherwise.
     */
    private VersionPart versionPart;

    /**
     * For a pipeline task, the URL of the GitHub Actions workflow to run; {@code null} otherwise.
     */
    private String workflowUrl;

    /**
     * Whether this stage runs after the previous stage finishes or together with it.
     */
    private RunMode runMode;

    /**
     * The kind of action a stage performs.
     */
    @Getter
    public enum StageTask {
        /** Commits a file change to a repository. */
        COMMIT("Commit"),
        /** Triggers a GitHub Actions pipeline. */
        WORKFLOW("Pipeline");

        /**
         * Human-readable label shown in the UI.
         */
        private final String label;

        StageTask(String label) {
            this.label = label;
        }
    }

    /**
     * The kind of change a commit stage makes to a file.
     */
    @Getter
    public enum CommitOperation {
        /** Increments the version in {@code pom.xml}. */
        BUMP_MAVEN_VERSION("Bump Maven version (pom.xml)"),
        /** Replaces text matching a regular expression. */
        FIND_AND_REPLACE_REGEX("Find & replace (regex)"),
        /** Overwrites a file with fixed content. */
        SET_FILE_CONTENT("Set file content");

        /**
         * Human-readable label shown in the UI.
         */
        private final String label;

        CommitOperation(String label) {
            this.label = label;
        }
    }

    /**
     * When a stage starts relative to the preceding stage.
     */
    @Getter
    public enum RunMode {
        /** Starts only after the preceding stage completes. */
        AFTER_PREVIOUS_FINISHES("After previous finishes"),
        /** Starts at the same time as the preceding stage. */
        WITH_PREVIOUS("With previous");

        /**
         * Human-readable label shown in the UI.
         */
        private final String label;

        RunMode(String label) {
            this.label = label;
        }
    }

    /**
     * Which component of a semantic version a Maven bump increments.
     */
    @Getter
    public enum VersionPart {
        /** Increments the major component, e.g. 1.2.3 → 2.0.0. */
        MAJOR("Major"),
        /** Increments the minor component, e.g. 1.2.3 → 1.3.0. */
        MINOR("Minor"),
        /** Increments the patch component, e.g. 1.2.3 → 1.2.4. */
        PATCH("Patch");

        /**
         * Human-readable label shown in the UI.
         */
        private final String label;

        VersionPart(String label) {
            this.label = label;
        }
    }

}
