package com.polemon.github_manager.controller.web;

import com.polemon.api_lib.identity.Identity;
import com.polemon.github_manager.exceptions.InvalidTokenException;
import com.polemon.github_manager.service.AuthService;
import com.polemon.github_manager.service.RepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Web controller serving the settings page: viewing/saving the GitHub token
 * and resetting the application's stored state.
 */
@Slf4j
@Controller
@RequestMapping("/settings")
public class SettingsController {

    /**
     * The service used to read, validate, and store the GitHub token.
     */
    private final AuthService authService;

    /**
     * The service used to clear tracked repositories on reset.
     */
    private final RepositoryService repositoryService;

    public SettingsController(AuthService authService, RepositoryService repositoryService) {
        this.authService = authService;
        this.repositoryService = repositoryService;
    }

    /**
     * Builds the settings page.
     *
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @return the settings content fragment or the full page layout
     */
    @GetMapping
    public String showSettingsPage(Model model,
                                   @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        return answerRequest(model, hxRequest);
    }

    /**
     * Validates and saves the submitted GitHub token.
     *
     * @param identity the caller, from auth's identity token
     * @param model       the model to populate for rendering
     * @param hxRequest   the {@code HX-Request} header, present when the request comes from HTMX
     * @param githubToken the GitHub token submitted by the user
     * @return the settings content fragment or the full page layout
     */
    @PostMapping
    public String saveSettings(Identity identity, Model model,
                               @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                               @RequestParam("github-token") String githubToken) {
        try {
            authService.setGithubToken(identity.userId(), githubToken);
            log.info("Successfully saved GitHub token");
        } catch (InvalidTokenException e) {
            log.warn("Invalid GitHub token provided: {}", e.getMessage());
            model.addAttribute("error", "Invalid or expired GitHub token");
        }

        return answerRequest(model, hxRequest);
    }

    /**
     * Clears the stored GitHub token and all tracked repositories, resetting
     * the application to its initial state.
     *
     * @param identity the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @return the settings content fragment or the full page layout
     */
    @PostMapping("/reset")
    public String resetSettings(Identity identity, Model model,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        authService.clear(identity.userId());
        repositoryService.clear(identity.userId());

        return answerRequest(model, hxRequest);
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
        if (Boolean.parseBoolean(hxRequest))
            return "pages/settings :: content";

        model.addAttribute("content", "pages/settings :: content");
        return "layouts/main";
    }

}
