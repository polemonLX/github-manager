package com.polemon.github_manager.client;

import com.polemon.github_manager.model.dto.api.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client for interacting with the GitHub REST API.
 * This class centralizes all outbound calls to api.GitHub.com and owns access
 * to the stored GitHub token used to authenticate those calls.
 */
@Slf4j
@Component
public class GitHubClient {

    /**
     * Base URL for the GitHub REST API.
     */
    private static final String API_BASE_URL = "https://api.github.com";

    /**
     * Page size used for paginated list endpoints (GitHub caps this at 100).
     */
    private static final int PAGE_SIZE = 100;

    /**
     * Package ecosystems queried when listing all packages for an organization.
     * GitHub requires the package type to be specified per call, so listing
     * everything means iterating these.
     */
    private static final List<String> PACKAGE_TYPES = List.of("container", "maven", "docker");

    /**
     * RestTemplate instance for making HTTP requests to the GitHub API.
     */
    private final RestTemplate restTemplate;

    /**
     * Mapper for parsing GitHub JSON responses.
     */
    private final ObjectMapper mapper;

    public GitHubClient() {
        this(new RestTemplate());
    }

    GitHubClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.mapper = new ObjectMapper();
    }

    /**
     * Checks whether the given GitHub token is accepted by the GitHub API.
     * The token is passed in explicitly because it is validated before being stored.
     *
     * @param token The candidate GitHub token to validate
     * @return {@code true} if GitHub accepts the token, {@code false} otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            HttpEntity<String> entity = new HttpEntity<>(buildHeaders(token));
            ResponseEntity<String> response = restTemplate.exchange(
                    API_BASE_URL + "/user",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("GitHub token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fetches the current core API rate limit for the stored token.
     * The /rate_limit endpoint itself does not consume the rate limit.
     *
     * @return The rate limit snapshot, or {@code null} if it cannot be retrieved
     */
    public RateLimitAPI getRateLimit(String token) {
        JsonNode root = fetchJson("/rate_limit", token);
        if (root == null)
            return null;

        JsonNode core = root.path("resources").path("core");
        return new RateLimitAPI(
                core.path("remaining").asInt(),
                core.path("limit").asInt(),
                Instant.ofEpochSecond(core.path("reset").asLong())
        );
    }

    /**
     * Checks whether the given repository exists and is accessible with the stored GitHub token.
     *
     * @param owner The repository owner
     * @param name  The repository name
     * @return {@code true} if the repository exists and is accessible, {@code false} otherwise
     */
    public boolean repositoryExists(String owner, String name, String token) {
        return fetchJson(String.format("/repos/%s/%s", owner, name), token) != null;
    }

    /**
     * Fetches the workflow runs that are currently active (queued or in progress)
     * for the given repository.
     *
     * @param owner The repository owner
     * @param name  The repository name
     * @return The list of active workflow runs, or an empty list if none / on failure
     */
    public List<WorkflowRunAPI> getActionsRunning(String owner, String name, String token) {
        List<WorkflowRunAPI> runs = new ArrayList<>();
        for (String status : new String[]{"queued", "in_progress"}) {
            String path = String.format("/repos/%s/%s/actions/runs?status=%s&per_page=100", owner, name, status);
            JsonNode root = fetchJson(path, token);
            if (root == null)
                continue;

            for (JsonNode node : root.path("workflow_runs"))
                runs.add(mapRun(node));
        }
        return runs;
    }

    /**
     * Finds the run a {@code workflow_dispatch} created. GitHub answers a
     * dispatch without the run, so the run is looked up afterwards: among the
     * workflow's latest dispatched runs on the branch, the oldest one created
     * at or after {@code since} (later ones were started by something else).
     * One API call.
     *
     * @param owner            The repository owner
     * @param name             The repository name
     * @param workflowFileName The workflow file name, e.g. {@code ci.yml}
     * @param branch           The branch the workflow was dispatched on
     * @param since            When the dispatch was sent, minus some clock skew
     * @return The run, or {@code null} if GitHub doesn't list it yet / on failure
     */
    public WorkflowRunAPI findDispatchedRun(String owner, String name, String workflowFileName, String branch, Instant since, String token) {
        String path = String.format("/repos/%s/%s/actions/workflows/%s/runs?event=workflow_dispatch&branch=%s&per_page=10",
                owner, name, workflowFileName, branch);
        JsonNode root = fetchJson(path, token);
        if (root == null)
            return null;

        WorkflowRunAPI found = null;
        for (JsonNode node : root.path("workflow_runs")) {
            WorkflowRunAPI run = mapRun(node);
            if (run.getCreatedAt() == null || run.getCreatedAt().isBefore(since))
                continue;
            if (found == null || run.getCreatedAt().isBefore(found.getCreatedAt()))
                found = run;
        }
        return found;
    }

    /**
     * Fetches one workflow run, to follow it until it completes. One API call.
     *
     * @param owner The repository owner
     * @param name  The repository name
     * @param runId The run id
     * @return The run, or {@code null} on failure
     */
    public WorkflowRunAPI getRun(String owner, String name, long runId, String token) {
        JsonNode root = fetchJson(String.format("/repos/%s/%s/actions/runs/%d", owner, name, runId), token);
        return root == null ? null : mapRun(root);
    }

    /**
     * Fetches the open pull requests for the given repository.
     *
     * @param owner The repository owner
     * @param name  The repository name
     * @return The list of open pull requests, or an empty list if none / on failure
     */
    public List<PullRequestAPI> getOpenPullRequests(String owner, String name, String token) {
        List<PullRequestAPI> pullRequests = new ArrayList<>();
        String path = String.format("/repos/%s/%s/pulls?state=open&per_page=100", owner, name);
        JsonNode root = fetchJson(path, token);
        if (root == null)
            return pullRequests;

        for (JsonNode node : root) {
            pullRequests.add(new PullRequestAPI(
                    node.path("number").asInt(),
                    node.path("title").asString(),
                    node.path("user").path("login").asString(),
                    parseInstant(node.path("created_at")),
                    node.path("html_url").asString()
            ));
        }
        return pullRequests;
    }

    /**
     * Fetches the branches for the given repository.
     *
     * @param owner The repository owner
     * @param name  The repository name
     * @return The list of branches, or an empty list if none / on failure
     */
    public List<BranchAPI> getBranches(String owner, String name, String token) {
        List<BranchAPI> branches = new ArrayList<>();
        String path = String.format("/repos/%s/%s/branches?per_page=100", owner, name);
        JsonNode root = fetchJson(path, token);
        if (root == null)
            return branches;

        for (JsonNode node : root) {
            branches.add(new BranchAPI(
                    node.path("name").asString(),
                    node.path("commit").path("sha").asString(),
                    node.path("protected").asBoolean()
            ));
        }
        return branches;
    }

    /**
     * Fetches the tags for the given repository.
     *
     * @param owner The repository owner
     * @param name  The repository name
     * @return The list of tags, or an empty list if none / on failure
     */
    public List<TagAPI> getTags(String owner, String name, String token) {
        List<TagAPI> tags = new ArrayList<>();
        String path = String.format("/repos/%s/%s/tags?per_page=100", owner, name);
        JsonNode root = fetchJson(path, token);
        if (root == null)
            return tags;

        for (JsonNode node : root) {
            tags.add(new TagAPI(
                    node.path("name").asString(),
                    node.path("commit").path("sha").asString(),
                    node.path("zipball_url").asString()
            ));
        }
        return tags;
    }

    /**
     * Fetches the registered workflows (pipeline definitions) for the given repository.
     *
     * @param owner The repository owner
     * @param name  The repository name
     * @return The list of registered workflows, or an empty list if none / on failure
     */
    public List<WorkflowAPI> getWorkflows(String owner, String name, String token) {
        List<WorkflowAPI> workflows = new ArrayList<>();
        String path = String.format("/repos/%s/%s/actions/workflows?per_page=100", owner, name);
        JsonNode root = fetchJson(path, token);
        if (root == null)
            return workflows;

        for (JsonNode node : root.path("workflows")) {
            workflows.add(new WorkflowAPI(
                    node.path("id").asLong(),
                    node.path("name").asString(),
                    node.path("state").asString(),
                    node.path("path").asString(),
                    node.path("html_url").asString()
            ));
        }
        return workflows;
    }

    /**
     * Fetches every package published by the given organization, across all known
     * package ecosystems, following pagination.
     * <p>
     * GitHub exposes packages per organization rather than per repository, so the
     * intended usage is to call this once per sync and then filter the result by
     * {@link PackageAPI#getRepositoryName()} for each tracked repository, rather than
     * calling the API once per repository.
     *
     * @param org The organization that owns the packages
     * @return The list of packages, or an empty list if none / on failure
     */
    public List<PackageAPI> getPackages(String org, String token) {
        List<PackageAPI> packages = new ArrayList<>();
        for (String packageType : PACKAGE_TYPES)
            packages.addAll(getPackages(org, packageType, token));
        return packages;
    }

    /**
     * Fetches the packages of a single ecosystem published by the organization,
     * following pagination until every page has been read.
     *
     * @param org         The organization that owns the packages
     * @param packageType The package ecosystem, e.g. {@code container}, {@code maven}, {@code npm}
     * @return The list of packages of that type, or an empty list if none / on failure
     */
    public List<PackageAPI> getPackages(String org, String packageType, String token) {
        List<PackageAPI> packages = new ArrayList<>();
        int page = 1;
        while (true) {
            String path = String.format("/orgs/%s/packages?package_type=%s&per_page=%d&page=%d", org, packageType, PAGE_SIZE, page);
            JsonNode root = fetchJson(path, token);
            if (root == null || !root.isArray() || root.isEmpty())
                break;

            for (JsonNode node : root)
                packages.add(mapPackage(node));

            if (root.size() < PAGE_SIZE)
                break;
            page++;
        }
        return packages;
    }

    /**
     * Fetches a single file from the given repository through the contents API.
     *
     * @param owner  The repository owner
     * @param name   The repository name
     * @param path   The path of the file within the repository
     * @param branch The branch to read from, or {@code null} for the default branch
     * @return The file with its decoded content and blob SHA, or {@code null} if
     * the file does not exist / on failure
     */
    public FileContentAPI getFileContent(String owner, String name, String path, String branch, String token) {
        String apiPath = String.format("/repos/%s/%s/contents/%s", owner, name, path);
        if (branch != null && !branch.isEmpty())
            apiPath += "?ref=" + branch;

        JsonNode root = fetchJson(apiPath, token);
        if (root == null)
            return null;

        String encoded = root.path("content").asString().replaceAll("\\s", "");
        String content = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        return new FileContentAPI(root.path("path").asString(), root.path("sha").asString(), content);
    }

    /**
     * Creates or updates a single file in the given repository through the
     * contents API, producing one commit on the target branch.
     *
     * @param owner   The repository owner
     * @param name    The repository name
     * @param path    The path of the file within the repository
     * @param branch  The branch to commit to, or {@code null} for the default branch
     * @param content The new plain-text content of the file
     * @param message The commit message
     * @param sha     The current blob SHA of the file (required by GitHub when
     *                updating an existing file), or {@code null} when creating it
     * @return {@code true} if the commit was created, {@code false} on failure
     */
    public boolean updateFileContent(String owner, String name, String path, String branch, String content, String message, String sha, String token) {
        String apiPath = String.format("/repos/%s/%s/contents/%s", owner, name, path);

        ObjectNode body = mapper.createObjectNode();
        body.put("message", message);
        body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        if (sha != null)
            body.put("sha", sha);
        if (branch != null && !branch.isEmpty())
            body.put("branch", branch);

        return sendJson(apiPath, HttpMethod.PUT, body, token) != null;
    }

    /**
     * Triggers a {@code workflow_dispatch} event for the given workflow file,
     * starting a new run on the given ref. GitHub responds with no body, so the
     * created run must be discovered afterwards by listing the workflow's runs.
     *
     * @param owner            The repository owner
     * @param name             The repository name
     * @param workflowFileName The workflow file name, e.g. {@code ci.yml}
     * @param ref              The branch or tag to run the workflow on
     * @return {@code true} if the dispatch was accepted, {@code false} on failure
     */
    public boolean dispatchWorkflow(String owner, String name, String workflowFileName, String ref, String token) {
        String apiPath = String.format("/repos/%s/%s/actions/workflows/%s/dispatches", owner, name, workflowFileName);

        ObjectNode body = mapper.createObjectNode();
        body.put("ref", ref);

        return sendJson(apiPath, HttpMethod.POST, body, token) != null;
    }

    /**
     * Maps a workflow run JSON node.
     *
     * @param node The run node from the GitHub API
     * @return The mapped run
     */
    private WorkflowRunAPI mapRun(JsonNode node) {
        return new WorkflowRunAPI(
                node.path("id").asLong(),
                node.path("workflow_id").asLong(),
                node.path("name").asString(),
                node.path("status").asString(),
                textOrNull(node.path("conclusion")),
                node.path("head_branch").asString(),
                node.path("event").asString(),
                node.path("html_url").asString(),
                parseInstant(node.path("created_at"))
        );
    }

    /**
     * Maps a single package JSON node to a {@link Package}.
     *
     * @param node The package node from the GitHub API
     * @return The mapped package
     */
    private PackageAPI mapPackage(JsonNode node) {
        return new PackageAPI(
                node.path("id").asLong(),
                node.path("name").asString(),
                node.path("package_type").asString(),
                node.path("visibility").asString(),
                node.path("version_count").asInt(),
                node.path("html_url").asString(),
                textOrNull(node.path("repository").path("name"))
        );
    }

    /**
     * Performs an authenticated GET against the given API path and parses the JSON body.
     * Centralizes token retrieval, request setup, and soft failure handling shared by
     * the read endpoints.
     *
     * @param path The API path relative to the base URL, e.g. {@code /repos/owner/name/branches}
     * @return The parsed response tree, or {@code null} if there is no token or the call fails
     */
    private JsonNode fetchJson(String path, String token) {
        if (token == null || token.isEmpty()) {
            log.warn("Cannot call {} - no GitHub token available", path);
            return null;
        }

        try {
            HttpEntity<String> entity = new HttpEntity<>(buildHeaders(token));
            ResponseEntity<String> response = restTemplate.exchange(
                    API_BASE_URL + path,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return mapper.readTree(response.getBody());
        } catch (RestClientException e) {
            log.warn("GitHub API call failed for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Performs an authenticated write (POST/PUT) with a JSON body against the given
     * API path. Centralizes request setup and soft failure handling shared by the
     * write endpoints, mirroring {@link #fetchJson(String, String)}.
     *
     * @param path   The API path relative to the base URL
     * @param method The HTTP method to use, e.g. {@link HttpMethod#POST}
     * @param body   The JSON body to send, or {@code null} for no body
     * @return The parsed response tree (an empty object for bodiless 2xx responses),
     * or {@code null} if there is no token or the call fails
     */
    private JsonNode sendJson(String path, HttpMethod method, JsonNode body, String token) {
        if (token == null || token.isEmpty()) {
            log.warn("Cannot call {} - no GitHub token available", path);
            return null;
        }

        try {
            HttpHeaders headers = buildHeaders(token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body == null ? null : body.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    API_BASE_URL + path,
                    method,
                    entity,
                    String.class
            );

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isEmpty())
                return mapper.createObjectNode();
            return mapper.readTree(responseBody);
        } catch (RestClientException e) {
            log.warn("GitHub API call failed for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Returns the text value of a node, or {@code null} when the node is missing or JSON null.
     *
     * @param node The node to read
     * @return The text value, or {@code null}
     */
    private String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    /**
     * Parses an ISO-8601 timestamp node into an {@link Instant}.
     *
     * @param node The node holding the timestamp
     * @return The parsed instant, or {@code null} if absent or unparseable
     */
    private Instant parseInstant(JsonNode node) {
        String value = textOrNull(node);
        if (value == null)
            return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            log.warn("Failed to parse timestamp '{}': {}", value, e.getMessage());
            return null;
        }
    }

    /**
     * Builds the standard GitHub API request headers for the given token.
     *
     * @param token The GitHub token to authenticate with
     * @return HttpHeaders carrying the authorization and accept headers
     */
    private HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        return headers;
    }

}
