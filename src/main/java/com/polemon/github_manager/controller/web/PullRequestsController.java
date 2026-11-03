package com.polemon.github_manager.controller.web;

import com.polemon.api_lib.identity.Identity;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.service.RepositoryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Web controller serving the pull requests page, which lists tracked
 * repositories that currently have open pull requests.
 */
@Controller
@RequestMapping("/pull-requests")
public class PullRequestsController {

    /**
     * The service used to retrieve and sync tracked repositories.
     */
    private final RepositoryService repositoryService;

    public PullRequestsController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * Builds the pull requests page, listing only tracked repositories that
     * have at least one open pull request.
     *
     * @param identity the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @return the pull requests content fragment or the full page layout
     */
    @GetMapping
    public String showPullRequestsPage(Identity identity, Model model,
                                       @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        List<RepositoryDTO> repositories = repositoryService.getRepositories(identity.userId()).stream()
                .filter(repo -> !repo.getOpenPullRequests().isEmpty())
                .toList();
        model.addAttribute("repositories", repositories);

        return answerRequest(model, hxRequest);
    }

    /**
     * Triggers a full synchronization of all tracked repositories and instructs
     * the client to refresh the page.
     *
     * @param identity the caller, from auth's identity token
     * @param response   the HTTP response, used to set the HTMX refresh header
     * @param repository unused; accepted for endpoint symmetry with other controllers
     * @return a redirect to the pull requests page
     */
    @PostMapping("/sync")
    public String syncRepositories(Identity identity, HttpServletResponse response,
                                   @RequestBody(required = false) RepositoryDTO repository) {
        if (repository != null)
            repositoryService.syncPullRequest(identity.userId(), repository.getOwner(), repository.getName());
        else
            repositoryService.syncPullRequest(identity.userId());

        response.setHeader("HX-Refresh", "true");
        return "redirect:/pull-requests";
    }

    /**
     * Renders either the bare content fragment (for HTMX requests) or the full
     * page layout wrapping it.
     *
     * @param model     the model to populate
     * @param hxRequest the {@code HX-Request} header value, if present
     * @return the view name to render
     */
    private String answerRequest(Model model, String hxRequest) {
        if ("true".equals(hxRequest))
            return "pages/pullRequests :: content";

        model.addAttribute("content", "pages/pullRequests :: content");
        return "layouts/main";
    }

}
