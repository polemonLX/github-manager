package com.polemon.github_manager.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExceptionsTest {

    @Test
    void keepTheMessageAndTheCause() {
        IllegalStateException cause = new IllegalStateException("GitHub down");

        for (Exception e : new Exception[]{new InvalidRepositoryException("why", cause), new InvalidTokenException("why", cause),
                new InvalidWorkflowException("why", cause)}) {
            assertEquals("why", e.getMessage());
            assertSame(cause, e.getCause());
        }
    }

}
