package com.polemon.github_manager.exceptions;

/**
 * Thrown when a repository fails validation, e.g. it already exists in the
 * tracked list, or does not exist on / is not accessible via GitHub.
 */
public class InvalidRepositoryException extends Exception {

    public InvalidRepositoryException(String message) {
        super(message);
    }

    public InvalidRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }

}
