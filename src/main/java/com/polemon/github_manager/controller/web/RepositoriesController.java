package com.polemon.github_manager.controller.web;

import com.polemon.api_lib.identity.Identity;
import com.polemon.github_manager.exceptions.InvalidRepositoryException;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.service.RepositoryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Web controller serving the repositories page: listing tracked repositories,
 * and handling add, remove, and sync actions.
 */
@Slf4j
@Controller
@RequestMapping("/repositories")
public class RepositoriesController {

    /**
     * The service used to manage tracked repositories.
     */
    private final RepositoryService repositoryService;

    public RepositoriesController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * Builds the repositories page, listing all tracked repositories.
     *
     * @param identity the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @return the repositories content fragment or the full page layout
     */
    @GetMapping
    public String showRepositoriesPage(Identity identity, Model model,
                                       @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        return answerRequest(identity, model, hxRequest);
    }

    /**
     * Validates and adds a new tracked repository. On success re-renders the
     * repositories fragment; on failure responds {@code 400} with an error
     * fragment so HTMX can show it in the modal without closing it.
     *
     * @param identity the caller, from auth's identity token
     * @param model      the model to populate for rendering
     * @param response   the HTTP response, used to set a 400 status on error
     * @param hxRequest  the {@code HX-Request} header, present when the request comes from HTMX
     * @param repository the owner/name of the repository to add (form-bound)
     * @return the repositories content fragment (success) or the error fragment (failure)
     */
    @PostMapping("/add")
    public String addRepository(Identity identity, Model model, HttpServletResponse response,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                @ModelAttribute RepositoryDTO repository) {
        try {
            if (repository.getOwner() == null || repository.getOwner().trim().isEmpty() ||
                    repository.getName() == null || repository.getName().trim().isEmpty())
                throw new IllegalArgumentException("Repository owner and name must not be empty");

            if (isInvalidGitHubRepository(repository.getOwner()) || isInvalidGitHubRepository(repository.getName()))
                throw new IllegalArgumentException("Repository owner and name contain invalid characters");

            repositoryService.addRepository(identity.userId(), new RepositoryDTO(repository.getOwner(), repository.getName()));
            log.info("Added repository: {}/{}", repository.getOwner(), repository.getName());

            return answerRequest(identity, model, hxRequest);
        } catch (IllegalArgumentException | InvalidRepositoryException e) {
            log.warn("Failed to add repository: https://github.com/{}/{}. Reason: {}", repository.getOwner(), repository.getName(), e.getMessage());
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("error", e.getMessage());
            return "pages/repositories :: addRepoError";
        }
    }

    /**
     * Removes a tracked repository.
     *
     * @param identity the caller, from auth's identity token
     * @param model      the model to populate for rendering
     * @param hxRequest  the {@code HX-Request} header, present when the request comes from HTMX
     * @param repository the owner/name of the repository to remove
     * @return the repositories content fragment or the full page layout
     */
    @DeleteMapping("/remove")
    public String removeRepository(Identity identity, Model model,
                                   @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                   @RequestBody RepositoryDTO repository) {
        repositoryService.removeRepository(identity.userId(), new RepositoryDTO(repository.getOwner(), repository.getName()));
        log.info("Removed repository: {}/{}", repository.getOwner(), repository.getName());
        return answerRequest(identity, model, hxRequest);
    }

    /**
     * Triggers a synchronization: of a single repository if one is provided in
     * the request body, or of all tracked repositories otherwise. Instructs the
     * client to refresh the page.
     *
     * @param identity the caller, from auth's identity token
     * @param response   the HTTP response, used to set the HTMX refresh header
     * @param repository the repository to sync, or {@code null} to sync all
     * @return a redirect to the repositories page
     */
    @PostMapping("/sync")
    public String syncRepositories(Identity identity, HttpServletResponse response,
                                   @RequestBody(required = false) RepositoryDTO repository) {
        if (repository != null) {
            log.info("Syncing repository: {}/{}", repository.getOwner(), repository.getName());
            repositoryService.sync(identity.userId(), repository.getOwner(), repository.getName());
        } else {
            log.info("Syncing all tracked repositories");
            repositoryService.sync(identity.userId());
        }

        response.setHeader("HX-Refresh", "true");
        return "redirect:/repositories";
    }

    /**
     * Renders either the bare content fragment (for HTMX requests) or the full
     * page layout wrapping it.
     *
     * @param identity  the caller, whose data the page shows
     * @param model     the model to populate
     * @param hxRequest the {@code HX-Request} header value, if present
     * @return the view name to render
     */
    private String answerRequest(Identity identity, Model model, String hxRequest) {
        model.addAttribute("repositories", repositoryService.getRepositories(identity.userId()));

        if ("true".equals(hxRequest))
            return "pages/repositories :: content";

        model.addAttribute("content", "pages/repositories :: content");
        return "layouts/main";
    }

    /**
     * Checks whether a repository owner or name contains characters outside
     * the allowed GitHub identifier set ({@code A-Z a-z 0-9 _ -}).
     *
     * @param name the owner or name to validate
     * @return {@code true} if the value is {@code null} or contains invalid characters
     */
    private boolean isInvalidGitHubRepository(String name) {
        return name == null || !name.matches("^[a-zA-Z0-9_-]+$");
    }

}
