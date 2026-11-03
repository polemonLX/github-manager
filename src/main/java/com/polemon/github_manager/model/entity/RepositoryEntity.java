package com.polemon.github_manager.model.entity;

import com.polemon.api_lib.persistence.annotation.Store;
import com.polemon.api_lib.persistence.annotation.Unique;
import com.polemon.api_lib.persistence.model.Entity;
import com.polemon.github_manager.model.domain.BranchDomain;
import com.polemon.github_manager.model.domain.PullRequestDomain;
import com.polemon.github_manager.model.domain.TagDomain;
import com.polemon.github_manager.model.domain.WorkflowDomain;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A GitHub repository tracked by at least one user, with its data as of the
 * last sync. One entry per repository, shared by every user who tracks it
 * (see {@link TrackedRepository}); pages read this data, never GitHub.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Store("repositories")
@Unique({"owner", "name"})
public class RepositoryEntity extends Entity {

    /**
     * The repository owner (user or organization).
     */
    private String owner;

    /**
     * The repository name.
     */
    private String name;

    /**
     * The branches as of the last sync (stored as JSON).
     */
    private List<BranchDomain> branches = new ArrayList<>();

    /**
     * The open pull requests as of the last sync (stored as JSON).
     */
    private List<PullRequestDomain> pullRequests = new ArrayList<>();

    /**
     * The tags, newest first and without the {@code v} prefix, as of the last
     * sync (stored as JSON).
     */
    private List<TagDomain> releases = new ArrayList<>();

    /**
     * The GitHub Actions workflows as of the last sync (stored as JSON).
     */
    private List<WorkflowDomain> workflows = new ArrayList<>();

    /**
     * The number of GitHub Actions runs in progress at the last sync.
     */
    private int actionsRunning;

    /**
     * When the repository was last synced, in epoch milliseconds; {@code 0} if never.
     */
    private long lastSyncedAt;

    public RepositoryEntity(String id, String owner, String name) {
        super(id);
        this.owner = owner;
        this.name = name;
    }

}
