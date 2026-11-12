package com.polemon.github_manager.model.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * A single stage of an {@link InternalWorkflowEntity}. A stage either commits a change
 * to a repository or triggers one of its GitHub Actions pipelines. Task-specific
 * fields are only populated for the relevant task; unused fields are
 * omitted from the persisted JSON.
 */
@Getter
@Setter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalWorkflowStageEntity {

    /**
     * The repository this stage acts on.
     */
    private RepositoryRef repository;

    /**
     * Whether this stage performs a commit or triggers a pipeline.
     */
    private String task;

    /**
     * For a commit task, the kind of change to make; {@code null} otherwise.
     */
    private String commitOperation;

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
     * For a Maven version bump, which version component to increment
     * (a {@code VersionPart} name); {@code null} otherwise.
     */
    private String versionPart;

    /**
     * For a pipeline task, the URL of the GitHub Actions workflow to run; {@code null} otherwise.
     */
    private String workflowUrl;

    /**
     * Whether this stage runs after the previous stage finishes or together with it.
     */
    private String runMode;

    /**
     * Identifies the repository a stage acts on, by owner and name.
     */
    @Getter
    @Setter
    @NoArgsConstructor(onConstructor_ = @JsonCreator)
    @AllArgsConstructor
    @ToString
    public static class RepositoryRef {

        /**
         * The repository owner (user or organization).
         */
        private String owner;

        /**
         * The repository name.
         */
        private String name;

    }

}
