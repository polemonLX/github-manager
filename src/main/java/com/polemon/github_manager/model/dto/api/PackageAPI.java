package com.polemon.github_manager.model.dto.api;

import lombok.*;

/**
 * A package published under an owner (user or organization).
 * Live data fetched from the GitHub API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PackageAPI {

    /**
     * GitHub's internal identifier for the package.
     */
    private long id;

    /**
     * The package name.
     */
    private String name;

    /**
     * Package ecosystem, e.g. {@code container}, {@code maven}, {@code npm}, {@code docker}.
     */
    private String type;

    /**
     * Visibility of the package, e.g. {@code public}, {@code private}.
     */
    private String visibility;

    /**
     * Number of published versions of this package.
     */
    private int versionCount;

    /**
     * URL of the package on GitHub.
     */
    private String url;

    /**
     * Name of the repository this package was published from, or {@code null} if unlinked.
     */
    private String repositoryName;

}
