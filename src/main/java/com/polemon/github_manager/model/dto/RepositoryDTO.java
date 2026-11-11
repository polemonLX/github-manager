package com.polemon.github_manager.model.dto;

import lombok.*;

import java.util.LinkedList;
import java.util.List;

/**
 * Transfer object representing a tracked repository's identity and aggregated
 * GitHub data for use in the view layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RepositoryDTO {

    /** The repository owner (user or organization). */
    private String owner;

    /** The repository name. */
    private String name;

    /** Branches classified as feature branches (prefix {@code feature/}). */
    private List<BranchDTO> featureBranches = new LinkedList<>();

    /** Branches classified as bugfix branches (prefix {@code bugfix/}). */
    private List<BranchDTO> bugfixBranches = new LinkedList<>();

    /** Branches that are neither feature, bugfix, release, nor the default branch. */
    private List<BranchDTO> otherBranches = new LinkedList<>();

    /** All branches on this repository. */
    private List<BranchDTO> allBranches = new LinkedList<>();

    /** The currently open pull requests on this repository. */
    private List<PullRequestDTO> openPullRequests = new LinkedList<>();

    /** The currently active (queued or in-progress) GitHub Actions runs. */
    private List<ActionsRunningDTO> actionsRunning = new LinkedList<>();

    /** The published releases (tags) of this repository. */
    private List<ReleaseDTO> releases = new LinkedList<>();

    /** The registered GitHub Actions workflows on this repository. */
    private List<WorkflowDTO> workflows = new LinkedList<>();

    /** Human-readable timestamp of the last successful sync, e.g. {@code 00:01:02 15/07/2026}. */
    private String lastSyncedAt;

    /** Ids of the user's groups this repository is in (filled on the repositories page only). */
    private List<String> groupIds = new LinkedList<>();

    public RepositoryDTO(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

}
