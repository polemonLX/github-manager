package com.polemon.github_manager.controller.api;

import com.polemon.api_lib.identity.Identity;
import com.polemon.github_manager.model.dto.RateLimitDTO;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.service.AuthService;
import com.polemon.github_manager.service.RepositoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller exposing small JSON/HTML fragment endpoints consumed by the
 * front-end (e.g. HTMX partial refreshes), as opposed to full page views.
 */
@Controller
@RequestMapping("/api")
public class ApiController {

    /**
     * The service used to read the stored GitHub token and rate limit.
     */
    private final AuthService authService;

    /**
     * The service used to read tracked repositories and their statistics.
     */
    private final RepositoryService repositoryService;

    /** Refresh interval in seconds for the statistics bar HTMX polling. */
    @Value("${github-manager.statsbar-refresh-interval-seconds}")
    private int statsbarRefreshIntervalSeconds;

    public ApiController(AuthService authService, RepositoryService repositoryService) {
        this.authService = authService;
        this.repositoryService = repositoryService;
    }

    /**
     * Builds the aggregated statistics bar fragment across the caller's tracked repositories
     * (repository count, open PRs, branch counts by type, rate limit and token status).
     *
     * @param identity the caller, from auth's identity token
     * @param model    the model to populate for rendering
     * @return the {@code statsbar} fragment template
     */
    @GetMapping("/statsbar")
    public String getAllStats(Identity identity, Model model) {
        int repositoryCount = 0;
        int openPrCount = 0;
        int featureBranchCount = 0;
        int bugfixBranchCount = 0;
        int otherBranchCount = 0;

        for (RepositoryDTO repository : repositoryService.getRepositories(identity.userId())) {
            repositoryCount++;
            openPrCount += repository.getOpenPullRequests().size();
            featureBranchCount += repository.getFeatureBranches().size();
            bugfixBranchCount += repository.getBugfixBranches().size();
            otherBranchCount += repository.getOtherBranches().size();
        }

        Map<String, Integer> stats = new HashMap<>();
        stats.put("repositoryCount", repositoryCount);
        stats.put("openPrCount", openPrCount);
        stats.put("featureBranchCount", featureBranchCount);
        stats.put("bugfixBranchCount", bugfixBranchCount);
        stats.put("otherBranchCount", otherBranchCount);

        RateLimitDTO rateLimit = authService.getRateLimit(identity.userId());
        String token = authService.getGithubToken(identity.userId());

        model.addAttribute("stats", stats);
        model.addAttribute("rateLimit", rateLimit);
        model.addAttribute("githubToken", mask(token));
        model.addAttribute("statsbarRefreshIntervalSeconds", statsbarRefreshIntervalSeconds);

        return "fragments/statsbar :: statsbar";
    }

    /**
     * Hides a token for display, keeping only its last 4 characters so the user
     * can tell which token is stored without the page ever holding it.
     *
     * @param token the token, or {@code null}
     * @return the masked token, or an empty string if there is none
     */
    static String mask(String token) {
        if (token == null || token.isEmpty())
            return "";
        return "****" + token.substring(Math.max(0, token.length() - 4));
    }

}
