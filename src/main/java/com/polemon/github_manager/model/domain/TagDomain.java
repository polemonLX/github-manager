package com.polemon.github_manager.model.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.*;

/**
 * A single tag on a repository.
 * Live data fetched from the GitHub API.
 */
@Getter
@Setter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
@ToString
public class TagDomain {

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
