package com.polemon.github_manager.model.dto.api;

import lombok.*;

/**
 * A single tag on a repository.
 * Live data fetched from the GitHub API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TagAPI {

    /**
     * The tag name.
     */
    private String name;

    /**
     * SHA of the commit the tag currently points to.
     */
    private String commitSha;

    /**
     * URL for downloading the tag as a zipball.
     */
    private String zipballUrl;

}
