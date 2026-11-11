package com.polemon.github_manager.client;

import com.polemon.github_manager.model.dto.api.WorkflowRunAPI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The workflow run calls of {@link GitHubClient}, against a mocked GitHub.
 */
class GitHubClientTest {

    private static final String API = "https://api.github.com";

    private static final Instant DISPATCHED = Instant.parse("2026-11-11T10:00:00Z");

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer github = MockRestServiceServer.bindTo(restTemplate).build();
    private final GitHubClient client = new GitHubClient(restTemplate);

    @Test
    void findsTheOldestDispatchedRunCreatedSinceTheDispatch() {
        github.expect(requestTo(API + "/repos/polemonLX/auth/actions/workflows/release.yml/runs?event=workflow_dispatch&branch=main&per_page=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "token t1"))
                .andRespond(withSuccess("""
                        {"workflow_runs": [
                          {"id": 9, "workflow_id": 3, "status": "queued", "created_at": "2026-11-11T10:02:00Z"},
                          {"id": 8, "workflow_id": 3, "status": "in_progress", "conclusion": null, "name": "Release",
                           "head_branch": "main", "event": "workflow_dispatch",
                           "html_url": "https://github.com/polemonLX/auth/actions/runs/8", "created_at": "2026-11-11T10:00:01Z"},
                          {"id": 7, "workflow_id": 3, "status": "completed", "created_at": "2026-11-11T09:59:00Z"},
                          {"id": 6, "workflow_id": 3, "status": "completed"}
                        ]}""", MediaType.APPLICATION_JSON));

        WorkflowRunAPI run = client.findDispatchedRun("polemonLX", "auth", "release.yml", "main", DISPATCHED, "t1");

        assertEquals(8, run.getId());
        assertEquals(3, run.getWorkflowId());
        assertEquals("in_progress", run.getStatus());
        assertNull(run.getConclusion());
        assertEquals("Release", run.getName());
        assertEquals("main", run.getBranch());
        assertEquals("workflow_dispatch", run.getEvent());
        assertEquals("https://github.com/polemonLX/auth/actions/runs/8", run.getUrl());
        assertEquals(Instant.parse("2026-11-11T10:00:01Z"), run.getCreatedAt());
        github.verify();
    }

    @Test
    void noDispatchedRunWhenGitHubListsNoneYetOrFails() {
        github.expect(requestTo(API + "/repos/polemonLX/auth/actions/workflows/ci.yml/runs?event=workflow_dispatch&branch=dev&per_page=10"))
                .andRespond(withSuccess("{\"workflow_runs\": [{\"id\": 7, \"created_at\": \"2026-11-11T09:00:00Z\"}]}", MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/repos/polemonLX/auth/actions/workflows/ci.yml/runs?event=workflow_dispatch&branch=dev&per_page=10"))
                .andRespond(withServerError());

        assertNull(client.findDispatchedRun("polemonLX", "auth", "ci.yml", "dev", DISPATCHED, "t1"));
        assertNull(client.findDispatchedRun("polemonLX", "auth", "ci.yml", "dev", DISPATCHED, "t1"));
        assertNull(client.findDispatchedRun("polemonLX", "auth", "ci.yml", "dev", DISPATCHED, null));
        github.verify();
    }

    @Test
    void readsOneRun() {
        github.expect(requestTo(API + "/repos/polemonLX/auth/actions/runs/8"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id": 8, "workflow_id": 3, "status": "completed", "conclusion": "success",
                         "created_at": "not a date"}""", MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/repos/polemonLX/auth/actions/runs/9")).andRespond(withServerError());

        WorkflowRunAPI run = client.getRun("polemonLX", "auth", 8, "t1");

        assertEquals("completed", run.getStatus());
        assertEquals("success", run.getConclusion());
        assertNull(run.getCreatedAt());
        assertNull(client.getRun("polemonLX", "auth", 9, "t1"));
        github.verify();
    }

    @Test
    void runsInProgressKnowTheirWorkflow() {
        github.expect(requestTo(API + "/repos/polemonLX/auth/actions/runs?status=queued&per_page=100"))
                .andRespond(withSuccess("{\"workflow_runs\": [{\"id\": 1, \"workflow_id\": 3}]}", MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/repos/polemonLX/auth/actions/runs?status=in_progress&per_page=100"))
                .andRespond(withSuccess("{\"workflow_runs\": [{\"id\": 2, \"workflow_id\": 4}]}", MediaType.APPLICATION_JSON));

        List<WorkflowRunAPI> runs = client.getActionsRunning("polemonLX", "auth", "t1");

        assertEquals(List.of(3L, 4L), runs.stream().map(WorkflowRunAPI::getWorkflowId).toList());
        github.verify();
    }

    @Test
    void aFailedListingOfRunsInProgressIsSkipped() {
        github.expect(requestTo(API + "/repos/polemonLX/auth/actions/runs?status=queued&per_page=100")).andRespond(withServerError());
        github.expect(requestTo(API + "/repos/polemonLX/auth/actions/runs?status=in_progress&per_page=100"))
                .andRespond(withSuccess("{\"workflow_runs\": [{\"id\": 2, \"workflow_id\": 4}]}", MediaType.APPLICATION_JSON));

        assertEquals(List.of(4L), client.getActionsRunning("polemonLX", "auth", "t1").stream().map(WorkflowRunAPI::getWorkflowId).toList());
        github.verify();
    }

}
