package com.polemon.github_manager.model.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RepositoryDomainTest {

    @Test
    void countsItsPullRequestsAndFormatsTheLastSync() {
        RepositoryDomain repository = new RepositoryDomain("polemonLX", "auth");

        assertEquals(0, repository.getOpenPullRequests());
        assertNull(repository.getLastSyncedAt());

        repository.setPullRequests(List.of(new PullRequestDomain(), new PullRequestDomain()));
        repository.setLastSyncedAt(Instant.parse("2026-11-12T10:00:00Z"));

        assertEquals(2, repository.getOpenPullRequests());
        assertNotNull(repository.getLastSyncedAt());
    }

}
