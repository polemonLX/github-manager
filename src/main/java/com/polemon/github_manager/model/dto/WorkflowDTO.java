package com.polemon.github_manager.model.dto;

import lombok.*;

/**
 * Transfer object representing a registered GitHub Actions workflow on a repository.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class WorkflowDTO {

    /** The workflow name. */
    private String name;

    /** URL of the workflow on GitHub. */
    private String url;

    /** Activation state of the workflow, e.g. {@code active}, {@code disabled_manually}. */
    private String state;

}
