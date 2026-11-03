package com.polemon.github_manager.model.domain;

import lombok.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate domain object for a tracked repository, combining its persisted
 * identity with live data fetched from the GitHub API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RepositoryDomain {

    /**
     * Formatter for the human-readable last-synced timestamp, e.g. {@code 00:01:02 15/07/2026},
     * rendered in the system default time zone.
     */
    private static final DateTimeFormatter LAST_SYNCED_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy").withZone(ZoneId.systemDefault());

    /**
     * The repository owner (user or organization).
     */
    private String owner;

    /**
     * The repository name.
     */
    private String name;

    /**
     * Number of currently open pull requests.
     */
    private int openPullRequests;

    /**
     * Number of branches classified as feature branches.
     */
    private int featureBranches;

    /**
     * Number of branches classified as bugfix branches.
     */
    private int bugfixBranches;

    /**
     * Number of branches that are neither feature nor bugfix branches.
     */
    private int otherBranches;

    /**
     * Number of currently queued or in-progress workflow runs.
     */
    private int actionsRunning;

    /**
     * Instant this aggregate was last refreshed from the GitHub API.
     */
    private Instant lastSyncedAt;

    /**
     * The releases (tags) published from this repository.
     */
    private List<TagDomain> releases = new ArrayList<>();

    /**
     * The currently open pull requests on this repository.
     */
    private List<PullRequestDomain> pullRequests = new ArrayList<>();

    /**
     * The registered workflows (pipeline definitions) on this repository.
     */
    private List<WorkflowDomain> workflows = new ArrayList<>();

    /**
     * The branches on this repository, as fetched from the GitHub API.
     */
    private List<BranchDomain> branches = new ArrayList<>();

    /**
     * The packages published from this repository.
     */
    private List<PackageDomain> packages = new ArrayList<>();

    public RepositoryDomain(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    /**
     * Computes the number of currently open pull requests from the cached list.
     *
     * @return the count of pull requests in {@link #pullRequests}
     */
    public int getOpenPullRequests() {
        return pullRequests.size();
    }

    /**
     * Renders {@link #lastSyncedAt} as a human-readable string, e.g. {@code 00:01:02 15/07/2026}.
     *
     * @return the formatted timestamp, or {@code null} if the repository has never been synced
     */
    public String getLastSyncedAt() {
        return lastSyncedAt == null ? null : LAST_SYNCED_FORMATTER.format(lastSyncedAt);
    }

}
