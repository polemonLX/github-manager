package com.polemon.github_manager.model.dto.api;

/**
 * A file fetched from a repository through the GitHub contents API.
 * Live data fetched from the GitHub API; never persisted.
 *
 * @param path    Path of the file within the repository.
 * @param sha     Blob SHA of the file, required by GitHub when updating it.
 * @param content Decoded (plain text) content of the file.
 */
public record FileContentAPI(String path, String sha, String content) {

}
