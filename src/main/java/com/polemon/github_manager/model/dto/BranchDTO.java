package com.polemon.github_manager.model.dto;

import lombok.*;

/**
 * Transfer object representing a single branch on a repository.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BranchDTO {

    /** The branch name. */
    private String name;

    /** Whether the branch has GitHub branch protection rules enabled. */
    private boolean protectedBranch;

}
