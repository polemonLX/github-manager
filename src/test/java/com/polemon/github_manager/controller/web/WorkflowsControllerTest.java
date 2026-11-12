package com.polemon.github_manager.controller.web;

import com.polemon.github_manager.exceptions.InvalidWorkflowException;
import com.polemon.github_manager.model.dto.InternalWorkflowDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowStageDTO;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.service.RepositoryService;
import com.polemon.github_manager.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class WorkflowsControllerTest {

    private static final String BODY = "{\"name\":\"release\",\"stages\":[{\"task\":\"COMMIT\"}]}";

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowsController(repositoryService, workflowService))
            .setCustomArgumentResolvers(new FixedIdentity())
            .build();

    @Test
    void saveStoresTheWorkflowForTheCallerAndRendersTheFragment() throws Exception {
        mvc.perform(post("/workflows/save").header("HX-Request", "true").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/workflows :: content"));

        verify(workflowService).saveWorkflow(eq("u1"), any(InternalWorkflowDTO.class));
    }

    @Test
    void anInvalidWorkflowIsA400WithTheReason() throws Exception {
        doThrow(new IllegalArgumentException("A workflow named 'release' already exists"))
                .when(workflowService).saveWorkflow(eq("u1"), any(InternalWorkflowDTO.class));

        mvc.perform(post("/workflows/save").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("A workflow named 'release' already exists"));
    }

    @Test
    void thePageHasWhatTheBuilderNeeds() throws Exception {
        List<RepositoryDTO> repositories = List.of(new RepositoryDTO("polemonLX", "auth"));
        List<InternalWorkflowDTO> workflows = List.of(new InternalWorkflowDTO("release", List.of()));
        when(repositoryService.getRepositories("u1")).thenReturn(repositories);
        when(workflowService.getWorkflows("u1")).thenReturn(workflows);

        mvc.perform(get("/workflows"))
                .andExpect(status().isOk())
                .andExpect(view().name("layouts/main"))
                .andExpect(model().attribute("content", "pages/workflows :: content"))
                .andExpect(model().attribute("repositories", repositories))
                .andExpect(model().attribute("workflows", workflows))
                .andExpect(model().attribute("stageTasks", InternalWorkflowStageDTO.StageTask.values()))
                .andExpect(model().attribute("commitOperations", InternalWorkflowStageDTO.CommitOperation.values()))
                .andExpect(model().attribute("runModes", InternalWorkflowStageDTO.RunMode.values()))
                .andExpect(model().attribute("versionParts", InternalWorkflowStageDTO.VersionPart.values()));

        mvc.perform(get("/workflows").header("HX-Request", "true"))
                .andExpect(view().name("pages/workflows :: content"));
    }

    @Test
    void updatesAndDeletesTheCallersWorkflows() throws Exception {
        mvc.perform(patch("/workflows/update").header("HX-Request", "true").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(view().name("pages/workflows :: content"));
        mvc.perform(delete("/workflows/delete/release").header("HX-Request", "true"))
                .andExpect(view().name("pages/workflows :: content"));

        verify(workflowService).updateWorkflow(eq("u1"), any(InternalWorkflowDTO.class));
        verify(workflowService).deleteWorkflow("u1", "release");
    }

    @Test
    void startsAndStopsAWorkflow() throws Exception {
        mvc.perform(post("/workflows/start").header("HX-Request", "true").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/workflows :: content"));
        mvc.perform(post("/workflows/stop").param("workflowName", "release").header("HX-Request", "true"))
                .andExpect(view().name("pages/workflows :: content"));

        verify(workflowService).startWorkflow(eq("u1"), any(InternalWorkflowDTO.class));
        verify(workflowService).stopWorkflow("u1", "release");
    }

    @Test
    void aWorkflowThatCantStartIsA400WithTheReason() throws Exception {
        doThrow(new InvalidWorkflowException("Workflow 'release' is already running"))
                .when(workflowService).startWorkflow(eq("u1"), any(InternalWorkflowDTO.class));

        mvc.perform(post("/workflows/start").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("pages/workflows :: startWorkflowError"))
                .andExpect(model().attribute("error", "Workflow 'release' is already running"));
    }

}
