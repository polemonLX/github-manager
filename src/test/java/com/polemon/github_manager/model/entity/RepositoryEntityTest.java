package com.polemon.github_manager.model.entity;

import com.polemon.github_manager.model.domain.BranchDomain;
import com.polemon.github_manager.model.domain.PullRequestDomain;
import com.polemon.github_manager.model.domain.TagDomain;
import com.polemon.github_manager.model.domain.WorkflowDomain;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryEntityTest {

    /**
     * postgresql-lib stores the list fields as JSONB with a plain Jackson mapper,
     * so the GitHub data must survive that round trip.
     */
    @Test
    void gitHubDataSurvivesTheJsonRoundTrip() {
        ObjectMapper mapper = new ObjectMapper();
        Instant createdAt = Instant.parse("2026-11-03T10:15:30Z");
        RepositoryEntity repository = new RepositoryEntity("r1", "polemonLX", "auth");
        repository.setBranches(List.of(new BranchDomain("main", "sha", true)));
        repository.setPullRequests(List.of(new PullRequestDomain(7, "title", "me", createdAt, "url")));
        repository.setReleases(List.of(new TagDomain("1.0.0", "sha", "zip")));
        repository.setWorkflows(List.of(new WorkflowDomain(1, "release", "active", "path", "url", false)));

        RepositoryEntity read = mapper.readValue(mapper.writeValueAsString(repository), RepositoryEntity.class);

        assertTrue(read.getBranches().getFirst().isProtectedBranch());
        assertEquals(createdAt, read.getPullRequests().getFirst().getCreatedAt());
        assertEquals("1.0.0", read.getReleases().getFirst().getName());
        assertEquals("release", read.getWorkflows().getFirst().getName());
    }

}
