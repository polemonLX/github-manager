package com.polemon.github_manager.controller.web;

import com.polemon.api_lib.identity.Identity;
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
 * statistics across all tracked repositories and shows the runs of the
 * user's internal workflows.
 */
@Slf4j
@Controller
@RequestMapping("/")
public class DashboardController {

    /**
     * The fragment with the workflow runs, refreshed on its own.
     */
    private static final String ACTIVE_WORKFLOWS_FRAGMENT = "pages/dashboard :: active-workflow-content";

    /**
     * The service used to retrieve tracked repositories and their pipelines.
     */
    private final RepositoryService repositoryService;

    /**
     * The service used to manage internal workflows.
     */
    private final WorkflowService workflowService;

    /**
     * Refresh interval in seconds for the active-workflow HTMX polling.
     */
    private final int activeWorkflowRefreshIntervalSeconds;

    public DashboardController(RepositoryService repositoryService, WorkflowService workflowService,
                               @Value("${github-manager.active-workflow-refresh-interval-seconds}") int activeWorkflowRefreshIntervalSeconds) {
        this.repositoryService = repositoryService;
        this.workflowService = workflowService;
        this.activeWorkflowRefreshIntervalSeconds = activeWorkflowRefreshIntervalSeconds;
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
     * running actions across all tracked repositories, with the workflow runs.
     *
     * @param identity  the caller, from auth's identity token
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
        addActiveWorkflows(identity, model);

        if ("true".equals(hxRequest))
            return "pages/dashboard :: content";

        model.addAttribute("content", "pages/dashboard :: content");
        return "layouts/main";
    }

    /**
     * Refreshes the workflow runs fragment, which is periodically updated via
     * HTMX polling.
     *
     * @param identity the caller, from auth's identity token
     * @param model    the model to populate for rendering
     * @return the workflow runs fragment
     */
    @GetMapping("/dashboards/active-workflow/refresh")
    public String refreshWorkflowStatus(Identity identity, Model model) {
        addActiveWorkflows(identity, model);
        return ACTIVE_WORKFLOWS_FRAGMENT;
    }

    /**
     * Stops a running workflow; its run stays listed as stopped.
     *
     * @param identity     the caller, from auth's identity token
     * @param workflowName the name of the workflow to stop
     * @param model        the model to populate for rendering
     * @return the workflow runs fragment
     */
    @PostMapping("/dashboards/active-workflow/stop")
    public String stopActiveWorkflow(Identity identity, @RequestParam String workflowName, Model model) {
        log.info("Stopping workflow: {}", workflowName);
        workflowService.stopWorkflow(identity.userId(), workflowName);
        addActiveWorkflows(identity, model);
        return ACTIVE_WORKFLOWS_FRAGMENT;
    }

    /**
     * Removes an ended workflow run from the list.
     *
     * @param identity     the caller, from auth's identity token
     * @param workflowName the name of the workflow whose run to remove
     * @param model        the model to populate for rendering
     * @return the workflow runs fragment
     */
    @PostMapping("/dashboards/active-workflow/dismiss")
    public String dismissActiveWorkflow(Identity identity, @RequestParam String workflowName, Model model) {
        workflowService.dismissWorkflow(identity.userId(), workflowName);
        addActiveWorkflows(identity, model);
        return ACTIVE_WORKFLOWS_FRAGMENT;
    }

    /**
     * Adds the workflow runs and their refresh interval to the model.
     *
     * @param identity the caller
     * @param model    the model
     */
    private void addActiveWorkflows(Identity identity, Model model) {
        model.addAttribute("activeWorkflowRefreshIntervalSeconds", activeWorkflowRefreshIntervalSeconds);
        model.addAttribute("activeWorkflows", workflowService.getInternalWorkflowActiveList(identity.userId()));
    }

}
