package com.polemon.github_manager.controller.web;

import com.polemon.github_manager.model.dto.ActionsRunningDTO;
import com.polemon.github_manager.model.dto.BranchDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowActiveDTO;
import com.polemon.github_manager.model.dto.PullRequestDTO;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.service.RepositoryService;
import com.polemon.github_manager.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class DashboardControllerTest {

    private static final List<InternalWorkflowActiveDTO> RUNS = List.of(new InternalWorkflowActiveDTO());

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardController(repositoryService, workflowService, 7))
            .setCustomArgumentResolvers(new FixedIdentity())
            .build();

    @BeforeEach
    void setUp() {
        when(workflowService.getInternalWorkflowActiveList("u1")).thenReturn(RUNS);
    }

    @Test
    void theRootGoesToTheDashboard() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void theDashboardSumsTheRepositoriesAndShowsTheWorkflowRuns() throws Exception {
        RepositoryDTO auth = new RepositoryDTO("polemonLX", "auth");
        auth.setOpenPullRequests(List.of(new PullRequestDTO()));
        auth.setAllBranches(List.of(new BranchDTO(), new BranchDTO()));
        auth.setActionsRunning(List.of(new ActionsRunningDTO()));
        when(repositoryService.getRepositories("u1")).thenReturn(List.of(auth, new RepositoryDTO("polemonLX", "api-lib")));

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("layouts/main"))
                .andExpect(model().attribute("content", "pages/dashboard :: content"))
                .andExpect(model().attribute("repositoryCount", 2))
                .andExpect(model().attribute("openPrCount", 1))
                .andExpect(model().attribute("branchCount", 2))
                .andExpect(model().attribute("actionsRunning", 1))
                .andExpect(model().attribute("activeWorkflows", RUNS))
                .andExpect(model().attribute("activeWorkflowRefreshIntervalSeconds", 7));

        mvc.perform(get("/dashboard").header("HX-Request", "true"))
                .andExpect(view().name("pages/dashboard :: content"));
    }

    @Test
    void theWorkflowRunsRefreshOnTheirOwn() throws Exception {
        mvc.perform(get("/dashboards/active-workflow/refresh"))
                .andExpect(view().name("pages/dashboard :: active-workflow-content"))
                .andExpect(model().attribute("activeWorkflows", RUNS))
                .andExpect(model().attribute("activeWorkflowRefreshIntervalSeconds", 7));
    }

    @Test
    void stopsAWorkflowAndShowsTheRuns() throws Exception {
        mvc.perform(post("/dashboards/active-workflow/stop").param("workflowName", "release/v2"))
                .andExpect(view().name("pages/dashboard :: active-workflow-content"))
                .andExpect(model().attribute("activeWorkflows", RUNS));

        verify(workflowService).stopWorkflow("u1", "release/v2");
    }

    @Test
    void dismissesAnEndedRunAndShowsTheRest() throws Exception {
        mvc.perform(post("/dashboards/active-workflow/dismiss").param("workflowName", "release"))
                .andExpect(view().name("pages/dashboard :: active-workflow-content"))
                .andExpect(model().attribute("activeWorkflows", RUNS));

        verify(workflowService).dismissWorkflow("u1", "release");
    }

}
