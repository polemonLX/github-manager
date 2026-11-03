package com.polemon.github_manager.model.dto.api;

import lombok.*;

/**
 * A single branch on a repository.
 * Live data fetched from the GitHub API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BranchAPI {

    /**
     * The branch name.
     */
    private String name;

    /**
     * SHA of the commit the branch currently points to.
     */
    private String commitSha;

    /**
     * Whether the branch has GitHub branch protection rules enabled.
     */
    private boolean protectedBranch;

}
