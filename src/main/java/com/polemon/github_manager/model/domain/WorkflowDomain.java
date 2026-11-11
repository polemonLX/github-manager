package com.polemon.github_manager.model.domain;

import lombok.*;

/**
 * A registered workflow (pipeline definition) on a repository.
 * Corresponds to a workflow file under {@code .github/workflows}.
 * Live data fetched from the GitHub API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class WorkflowDomain {

    /**
     * GitHub's internal identifier for the workflow.
     */
    private long id;

    /**
     * The workflow name.
     */
    private String name;

    /**
     * Activation state of the workflow, e.g. {@code active}, {@code disabled_manually}.
     */
    private String state;

    /**
     * Path to the workflow definition, e.g. {@code .github/workflows/ci.yml}.
     */
    private String path;

    /**
     * URL of the workflow on GitHub.
     */
    private String url;

    /**
     * Whether a run of this workflow was queued or in progress at the last
     * sync. Stored with the repository's JSON, so older rows read as
     * {@code false}.
     */
    private boolean running;

}
