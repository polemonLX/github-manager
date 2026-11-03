package com.polemon.github_manager.exceptions;

/**
 * Thrown when a workflow fails validation, e.g. it does not exist or cannot be executed.
 */
public class InvalidWorkflowException extends Exception {

    public InvalidWorkflowException(String message) {
        super(message);
    }

    public InvalidWorkflowException(String message, Throwable cause) {
        super(message, cause);
    }

}
