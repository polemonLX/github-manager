package com.polemon.github_manager.exceptions;

/**
 * Thrown when a GitHub token fails validation, e.g. because it is invalid,
 * expired, or rejected by the GitHub API.
 */
public class InvalidTokenException extends Exception {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }

}
