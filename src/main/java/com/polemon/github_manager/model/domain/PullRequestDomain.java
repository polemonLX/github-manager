package com.polemon.github_manager.model.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.*;

import java.time.Instant;

/**
 * A single open pull request on a repository.
 * Live data fetched from the GitHub API.
 */
@Getter
@Setter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
@ToString
public class PullRequestDomain {

    /**
     * The pull request number within its repository.
     */
    private int number;

    /**
     * The pull request title.
     */
    private String title;

    /**
     * GitHub username of the pull request author.
     */
    private String author;

    /**
     * Instant the pull request was created.
     */
    private Instant createdAt;

    /**
     * URL of the pull request on GitHub.
     */
    private String url;

}
