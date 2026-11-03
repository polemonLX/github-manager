package com.polemon.github_manager.controller.web;

import com.polemon.api_lib.identity.Identity;
import com.polemon.github_manager.model.dto.InternalWorkflowActiveDTO;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.service.RepositoryService;
import com.polemon.github_manager.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

/**
 * Web controller serving the dashboard page, which summarizes aggregate
 * statistics across all tracked repositories.
 */
@Slf4j
@Controller
@RequestMapping("/")
public class DashboardController {

    /**
     * The service used to retrieve tracked repositories and their pipelines.
     */
    private final RepositoryService repositoryService;

    /**
     * The service used to manage internal workflows.
     */
    private final WorkflowService workflowService;

    /** Refresh interval in seconds for the active-workflow HTMX polling. */
    @Value("${github-manager.active-workflow-refresh-interval-seconds}")
    private int activeWorkflowRefreshIntervalSeconds;

    public DashboardController(RepositoryService repositoryService, WorkflowService workflowService) {
        this.repositoryService = repositoryService;
        this.workflowService = workflowService;
    }

    /**
     * Redirects the application root to the dashboard page.
     *
     * @return a redirect view pointing to {@code /dashboard}
     */
    @GetMapping
    public RedirectView redirectToDashboard() {
        return new RedirectView("/dashboard", true);
    }

    /**
     * Builds the dashboard page, aggregating open PR count, branch count, and
     * running actions across all tracked repositories.
     *
     * @param identity the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @return the dashboard content fragment or the full page layout
     */
    @GetMapping("/dashboard")
    public String showDashboardPage(Identity identity, Model model, @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        List<RepositoryDTO> repositories = repositoryService.getRepositories(identity.userId());

        int openPrCount = 0;
        int branchCount = 0;
        int actionsRunning = 0;
        for (RepositoryDTO repository : repositories) {
            openPrCount += repository.getOpenPullRequests().size();
            branchCount += repository.getAllBranches().size();
            actionsRunning += repository.getActionsRunning().size();
        }

        model.addAttribute("repositoryCount", repositories.size());
        model.addAttribute("openPrCount", openPrCount);
        model.addAttribute("branchCount", branchCount);
        model.addAttribute("actionsRunning", actionsRunning);
        model.addAttribute("repositories", repositories);

        return answerRequest(model, hxRequest);
    }

    /**
     * Refreshes the active workflow status fragment, which is periodically
     * updated via HTMX polling.
     *
     * @param identity the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @return the active workflow content fragment or the full page layout
     */
    @GetMapping("/dashboards/active-workflow/refresh")
    public String refreshWorkflowStatus(Identity identity, Model model, @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        model.addAttribute("activeWorkflowRefreshIntervalSeconds", activeWorkflowRefreshIntervalSeconds);

        List<InternalWorkflowActiveDTO> activeWorkflows = workflowService.getInternalWorkflowActiveList(identity.userId());
        model.addAttribute("activeWorkflows", activeWorkflows);

        if ("true".equals(hxRequest))
            return "pages/dashboard :: active-workflow-content";

        model.addAttribute("content", "pages/dashboard :: content");
        return "layouts/main";
    }

    /**
     * Stops the active workflow with the given name and re-renders the dashboard.
     *
     * @param identity the caller, from auth's identity token
     * @param workflowName the name of the workflow to stop
     * @param model        the model to populate for rendering
     * @param hxRequest    the {@code HX-Request} header, present when the request comes from HTMX
     * @return the dashboard content fragment or the full page layout
     */
    @PostMapping("/dashboards/active-workflow/stop/{workflowName}")
    public String stopActiveWorkflow(Identity identity, @PathVariable String workflowName, Model model,
                                     @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        log.info("Stopped active workflow: {}", workflowName);
        workflowService.stopWorkflow(identity.userId(), workflowName);
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
        if ("true".equals(hxRequest))
            return "pages/dashboard :: content";

        model.addAttribute("content", "pages/dashboard :: content");
        return "layouts/main";
    }

}
