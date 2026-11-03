package com.polemon.github_manager.model.dto;

import lombok.*;

import java.time.Instant;

/**
 * Transfer object representing a single open pull request on a repository.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PullRequestDTO {

    /** The pull request number within its repository. */
    private int number;

    /** The pull request title. */
    private String title;

    /** GitHub username of the pull request author. */
    private String author;

    /** URL of the pull request on GitHub. */
    private String url;

    /** Instant the pull request was created. */
    private Instant createdAt;

}
