package com.polemon.github_manager.client;

import com.polemon.github_manager.model.dto.api.BranchAPI;
import com.polemon.github_manager.model.dto.api.FileContentAPI;
import com.polemon.github_manager.model.dto.api.PackageAPI;
import com.polemon.github_manager.model.dto.api.PullRequestAPI;
import com.polemon.github_manager.model.dto.api.RateLimitAPI;
import com.polemon.github_manager.model.dto.api.TagAPI;
import com.polemon.github_manager.model.dto.api.WorkflowAPI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * The repository data, file and dispatch calls of {@link GitHubClient},
 * against a mocked GitHub.
 */
class GitHubClientCallsTest {

    private static final String API = "https://api.github.com";
    private static final String REPO = API + "/repos/polemonLX/auth";

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer github = MockRestServiceServer.bindTo(restTemplate).build();
    private final GitHubClient client = new GitHubClient(restTemplate);

    @Test
    void aTokenIsValidWhenGitHubAcceptsIt() {
        github.expect(requestTo(API + "/user")).andExpect(header("Authorization", "token good"))
                .andExpect(header("Accept", "application/vnd.github.v3+json"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/user")).andRespond(withUnauthorizedRequest());
        github.expect(requestTo(API + "/user")).andRespond(withServerError());

        assertTrue(client.isTokenValid("good"));
        assertFalse(client.isTokenValid("bad"));
        assertFalse(client.isTokenValid("good"));
        github.verify();
    }

    @Test
    void readsTheCoreRateLimit() {
        github.expect(requestTo(API + "/rate_limit")).andRespond(withSuccess(
                "{\"resources\": {\"core\": {\"remaining\": 4990, \"limit\": 5000, \"reset\": 1794448800}}}", MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/rate_limit")).andRespond(withServerError());

        assertEquals(new RateLimitAPI(4990, 5000, Instant.ofEpochSecond(1794448800)), client.getRateLimit("t1"));
        assertNull(client.getRateLimit("t1"));
        github.verify();
    }

    @Test
    void aRepositoryExistsWhenGitHubShowsIt() {
        github.expect(requestTo(REPO)).andRespond(withSuccess("{\"id\": 1}", MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/repos/polemonLX/ghost")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(client.repositoryExists("polemonLX", "auth", "t1"));
        assertFalse(client.repositoryExists("polemonLX", "ghost", "t1"));
        github.verify();
    }

    @Test
    void readsTheOpenPullRequestsBranchesTagsAndWorkflows() {
        github.expect(requestTo(REPO + "/pulls?state=open&per_page=100")).andRespond(withSuccess("""
                [{"number": 7, "title": "Groups", "user": {"login": "polemonLX"}, "created_at": "2026-11-11T10:00:00Z",
                  "html_url": "https://github.com/polemonLX/auth/pull/7"}]""", MediaType.APPLICATION_JSON));
        github.expect(requestTo(REPO + "/branches?per_page=100")).andRespond(withSuccess("""
                [{"name": "main", "commit": {"sha": "abc"}, "protected": true}]""", MediaType.APPLICATION_JSON));
        github.expect(requestTo(REPO + "/tags?per_page=100")).andRespond(withSuccess("""
                [{"name": "v1.0.5", "commit": {"sha": "def"}, "zipball_url": "zip"}]""", MediaType.APPLICATION_JSON));
        github.expect(requestTo(REPO + "/actions/workflows?per_page=100")).andRespond(withSuccess("""
                {"workflows": [{"id": 3, "name": "release", "state": "active", "path": ".github/workflows/release.yml",
                  "html_url": "https://github.com/polemonLX/auth/actions/workflows/release.yml"}]}""", MediaType.APPLICATION_JSON));

        PullRequestAPI pullRequest = client.getOpenPullRequests("polemonLX", "auth", "t1").getFirst();
        BranchAPI branch = client.getBranches("polemonLX", "auth", "t1").getFirst();
        TagAPI tag = client.getTags("polemonLX", "auth", "t1").getFirst();
        WorkflowAPI workflow = client.getWorkflows("polemonLX", "auth", "t1").getFirst();

        assertEquals(7, pullRequest.getNumber());
        assertEquals("Groups", pullRequest.getTitle());
        assertEquals("polemonLX", pullRequest.getAuthor());
        assertEquals(Instant.parse("2026-11-11T10:00:00Z"), pullRequest.getCreatedAt());
        assertEquals("https://github.com/polemonLX/auth/pull/7", pullRequest.getUrl());
        assertEquals("main", branch.getName());
        assertEquals("abc", branch.getCommitSha());
        assertTrue(branch.isProtectedBranch());
        assertEquals("v1.0.5", tag.getName());
        assertEquals("def", tag.getCommitSha());
        assertEquals("zip", tag.getZipballUrl());
        assertEquals(3, workflow.getId());
        assertEquals("active", workflow.getState());
        assertEquals(".github/workflows/release.yml", workflow.getPath());
        github.verify();
    }

    @Test
    void listsAreEmptyWhenGitHubFails() {
        github.expect(requestTo(REPO + "/pulls?state=open&per_page=100")).andRespond(withServerError());
        github.expect(requestTo(REPO + "/branches?per_page=100")).andRespond(withServerError());
        github.expect(requestTo(REPO + "/tags?per_page=100")).andRespond(withServerError());
        github.expect(requestTo(REPO + "/actions/workflows?per_page=100")).andRespond(withServerError());

        assertTrue(client.getOpenPullRequests("polemonLX", "auth", "t1").isEmpty());
        assertTrue(client.getBranches("polemonLX", "auth", "t1").isEmpty());
        assertTrue(client.getTags("polemonLX", "auth", "t1").isEmpty());
        assertTrue(client.getWorkflows("polemonLX", "auth", "t1").isEmpty());
        github.verify();
    }

    @Test
    void readsEveryPageOfEveryPackageType() {
        String fullPage = IntStream.range(0, 100)
                .mapToObj(i -> "{\"id\": " + i + ", \"name\": \"p" + i + "\", \"package_type\": \"container\", \"visibility\": \"private\","
                        + " \"version_count\": 2, \"html_url\": \"u\", \"repository\": {\"name\": \"auth\"}}")
                .collect(Collectors.joining(",", "[", "]"));
        github.expect(requestTo(API + "/orgs/polemon/packages?package_type=container&per_page=100&page=1"))
                .andRespond(withSuccess(fullPage, MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/orgs/polemon/packages?package_type=container&per_page=100&page=2"))
                .andRespond(withSuccess("[{\"id\": 100, \"name\": \"last\", \"repository\": null}]", MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/orgs/polemon/packages?package_type=maven&per_page=100&page=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        github.expect(requestTo(API + "/orgs/polemon/packages?package_type=docker&per_page=100&page=1"))
                .andRespond(withServerError());

        List<PackageAPI> packages = client.getPackages("polemon", "t1");

        assertEquals(101, packages.size());
        assertEquals("p0", packages.getFirst().getName());
        assertEquals("container", packages.getFirst().getType());
        assertEquals("private", packages.getFirst().getVisibility());
        assertEquals(2, packages.getFirst().getVersionCount());
        assertEquals("auth", packages.getFirst().getRepositoryName());
        assertNull(packages.getLast().getRepositoryName());
        github.verify();
    }

    @Test
    void readsAFileOnABranchOrTheDefaultOne() {
        String encoded = Base64.getMimeEncoder().encodeToString("<version>1.0.0</version>".getBytes(StandardCharsets.UTF_8));
        github.expect(requestTo(REPO + "/contents/pom.xml?ref=dev")).andRespond(withSuccess(
                "{\"path\": \"pom.xml\", \"sha\": \"s1\", \"content\": \"" + encoded.replace("\r\n", "\\n") + "\"}", MediaType.APPLICATION_JSON));
        github.expect(requestTo(REPO + "/contents/README.md")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        github.expect(requestTo(REPO + "/contents/VERSION")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(new FileContentAPI("pom.xml", "s1", "<version>1.0.0</version>"), client.getFileContent("polemonLX", "auth", "pom.xml", "dev", "t1"));
        assertNull(client.getFileContent("polemonLX", "auth", "README.md", null, "t1"));
        assertNull(client.getFileContent("polemonLX", "auth", "VERSION", "", "t1"));
        github.verify();
    }

    @Test
    void commitsAFileWithItsShaOnABranchOrCreatesOne() {
        github.expect(requestTo(REPO + "/contents/pom.xml")).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"message\": \"Bump\", \"content\": \"" + base64("new") + "\", \"sha\": \"s1\", \"branch\": \"dev\"}", true))
                .andRespond(withSuccess("{\"commit\": {\"sha\": \"c1\"}}", MediaType.APPLICATION_JSON));
        github.expect(requestTo(REPO + "/contents/VERSION")).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"message\": \"Create\", \"content\": \"" + base64("1.0.0") + "\"}", true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        github.expect(requestTo(REPO + "/contents/VERSION")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertTrue(client.updateFileContent("polemonLX", "auth", "pom.xml", "dev", "new", "Bump", "s1", "t1"));
        assertTrue(client.updateFileContent("polemonLX", "auth", "VERSION", "", "1.0.0", "Create", null, "t1"));
        assertFalse(client.updateFileContent("polemonLX", "auth", "VERSION", null, "1.0.0", "Create", null, "t1"));
        github.verify();
    }

    @Test
    void dispatchesAWorkflowOnARef() {
        github.expect(requestTo(REPO + "/actions/workflows/release.yml/dispatches")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(content().json("{\"ref\": \"main\"}", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        github.expect(requestTo(REPO + "/actions/workflows/release.yml/dispatches")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertTrue(client.dispatchWorkflow("polemonLX", "auth", "release.yml", "main", "t1"));
        assertFalse(client.dispatchWorkflow("polemonLX", "auth", "release.yml", "gone", "t1"));
        github.verify();
    }

    @Test
    void nothingIsCalledWithoutAToken() {
        assertNull(client.getRateLimit(null));
        assertNull(client.getRateLimit(""));
        assertFalse(client.dispatchWorkflow("polemonLX", "auth", "release.yml", "main", null));
        assertFalse(client.dispatchWorkflow("polemonLX", "auth", "release.yml", "main", ""));
        assertNull(new GitHubClient().getRateLimit(null));
        github.verify();
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

}
