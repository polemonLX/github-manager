package com.polemon.github_manager.controller.web;

import com.polemon.api_lib.identity.Identity;
import com.polemon.api_lib.log.LogSafe;
import com.polemon.github_manager.exceptions.InvalidRepositoryException;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.model.dto.RepositoryGroupDTO;
import com.polemon.github_manager.service.RepositoryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * Builds the repositories page, listing the tracked repositories, all or
     * those of one group.
     *
     * @param identity  the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @param group     the id of the group to show, if any
     * @return the repositories content fragment or the full page layout
     */
    @GetMapping
    public String showRepositoriesPage(Identity identity, Model model,
                                       @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                       @RequestParam(value = "group", required = false) String group) {
        return answerRequest(identity, model, hxRequest, group);
    }

    /**
     * Creates a repository group. On success re-renders the repositories
     * fragment; on failure responds {@code 400} with an error fragment for the
     * create-group modal, which stays open.
     *
     * @param identity  the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param response  the HTTP response, used to set a 400 status on error
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @param name      the new group's name
     * @param group     the group shown when the form was sent, kept as the filter
     * @return the repositories content fragment (success) or the error fragment (failure)
     */
    @PostMapping("/groups")
    public String createGroup(Identity identity, Model model, HttpServletResponse response,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                              @RequestParam(value = "name", required = false) String name,
                              @RequestParam(value = "group", required = false) String group) {
        try {
            repositoryService.createGroup(identity.userId(), name);
            return answerRequest(identity, model, hxRequest, group);
        } catch (IllegalArgumentException e) {
            log.debug("User {} can't create group '{}': {}", identity.userId(), LogSafe.of(name), LogSafe.of(e.getMessage()));
            return badRequest(model, response, e);
        }
    }

    /**
     * Deletes one of the caller's groups (only the group and its memberships;
     * the repositories stay), then re-renders the repositories fragment. When
     * the deleted group was the filter shown, the page goes back to all
     * repositories and the browser's address follows ({@code HX-Push-Url}).
     *
     * @param identity  the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param response  the HTTP response, for the {@code HX-Push-Url} header
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @param groupId   the group to delete
     * @param group     the group shown when the button was pressed, kept as the
     *                  filter unless it is the one deleted
     * @return the repositories content fragment or the full page layout
     */
    @PostMapping("/groups/delete")
    public String deleteGroup(Identity identity, Model model, HttpServletResponse response,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                              @RequestParam("groupId") String groupId,
                              @RequestParam(value = "group", required = false) String group) {
        repositoryService.deleteGroup(identity.userId(), groupId);
        if (groupId.equals(group)) {
            response.setHeader("HX-Push-Url", "/repositories");
            return answerRequest(identity, model, hxRequest, null);
        }
        return answerRequest(identity, model, hxRequest, group);
    }

    /**
     * Saves the groups a repository is in, from the edit modal. On success
     * re-renders the repositories fragment; on failure responds {@code 400}
     * with an error fragment for the edit modal, which stays open.
     *
     * @param identity  the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param response  the HTTP response, used to set a 400 status on error
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @param owner     the repository owner
     * @param name      the repository name
     * @param groupIds  the groups it must be in; none checked means none
     * @param group     the group shown when the form was sent, kept as the filter
     * @return the repositories content fragment (success) or the error fragment (failure)
     */
    @PostMapping("/edit")
    public String editRepository(Identity identity, Model model, HttpServletResponse response,
                                 @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                 @RequestParam("owner") String owner, @RequestParam("name") String name,
                                 @RequestParam(value = "groupIds", required = false) List<String> groupIds,
                                 @RequestParam(value = "group", required = false) String group) {
        try {
            repositoryService.setGroups(identity.userId(), owner, name, groupIds);
            return answerRequest(identity, model, hxRequest, group);
        } catch (IllegalArgumentException e) {
            log.debug("User {} can't set the groups of {}/{}: {}", identity.userId(), LogSafe.of(owner), LogSafe.of(name), LogSafe.of(e.getMessage()));
            return badRequest(model, response, e);
        }
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
                                @ModelAttribute RepositoryDTO repository,
                                @RequestParam(value = "group", required = false) String group) {
        try {
            if (repository.getOwner() == null || repository.getOwner().trim().isEmpty() ||
                    repository.getName() == null || repository.getName().trim().isEmpty())
                throw new IllegalArgumentException("Repository owner and name must not be empty");

            if (isInvalidGitHubRepository(repository.getOwner()) || isInvalidGitHubRepository(repository.getName()))
                throw new IllegalArgumentException("Repository owner and name contain invalid characters");

            repositoryService.addRepository(identity.userId(), new RepositoryDTO(repository.getOwner(), repository.getName()));
            log.info("User {} added repository {}/{}", identity.userId(), repository.getOwner(), repository.getName());

            return answerRequest(identity, model, hxRequest, group);
        } catch (IllegalArgumentException | InvalidRepositoryException e) {
            log.debug("User {} can't add repository {}/{}: {}", identity.userId(), LogSafe.of(repository.getOwner()), LogSafe.of(repository.getName()),
                    LogSafe.of(e.getMessage()));
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
        log.info("User {} removed repository {}/{}", identity.userId(), LogSafe.of(repository.getOwner()), LogSafe.of(repository.getName()));
        return answerRequest(identity, model, hxRequest, null);
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
            log.info("User {} syncs repository {}/{}", identity.userId(), LogSafe.of(repository.getOwner()), LogSafe.of(repository.getName()));
            repositoryService.sync(identity.userId(), repository.getOwner(), repository.getName());
        } else {
            log.info("User {} syncs all its repositories", identity.userId());
            repositoryService.sync(identity.userId());
        }

        response.setHeader("HX-Refresh", "true");
        return "redirect:/repositories";
    }

    /**
     * Answers a failed group change with {@code 400} and the error fragment
     * shown inside the open modal.
     *
     * @param model    the model to populate
     * @param response the HTTP response, set to 400
     * @param e        the reason
     * @return the error fragment
     */
    private static String badRequest(Model model, HttpServletResponse response, IllegalArgumentException e) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("error", e.getMessage());
        return "pages/repositories :: addRepoError";
    }

    /**
     * Renders either the bare content fragment (for HTMX requests) or the full
     * page layout wrapping it.
     *
     * @param identity  the caller, whose data the page shows
     * @param model     the model to populate
     * @param hxRequest the {@code HX-Request} header value, if present
     * @param group     the id of the group to show, or {@code null} for all;
     *                  an id that isn't one of the user's groups shows all
     * @return the view name to render
     */
    private String answerRequest(Identity identity, Model model, String hxRequest, String group) {
        List<RepositoryGroupDTO> groups = repositoryService.getGroups(identity.userId());
        String selected = groups.stream().map(RepositoryGroupDTO::id).filter(id -> id.equals(group)).findFirst().orElse(null);
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroup", selected);
        model.addAttribute("repositories", repositoryService.getRepositories(identity.userId(), selected));

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
