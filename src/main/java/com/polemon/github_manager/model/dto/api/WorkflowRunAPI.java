package com.polemon.github_manager.model.dto.api;

import lombok.*;

import java.time.Instant;

/**
 * A single execution of a workflow (an Actions run) on a repository.
 * Live data fetched from the GitHub API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class WorkflowRunAPI {

    /**
     * GitHub's internal identifier for the run.
     */
    private long id;

    /**
     * GitHub's identifier of the workflow (Action) this run belongs to.
     */
    private long workflowId;

    /**
     * Display name of the run (typically the workflow name).
     */
    private String name;

    /**
     * Lifecycle state of the run, e.g. {@code queued}, {@code in_progress}, {@code completed}.
     */
    private String status;

    /**
     * Final result once the run is completed, e.g. {@code success}, {@code failure}, {@code cancelled}.
     * {@code null} while the run is still active.
     */
    private String conclusion;

    /**
     * Branch the run was triggered on.
     */
    private String branch;

    /**
     * Event that triggered the run, e.g. {@code push}, {@code pull_request}.
     */
    private String event;

    /**
     * URL of the run on GitHub.
     */
    private String url;

    /**
     * Instant the run was created.
     */
    private Instant createdAt;

}
