package com.polemon.github_manager.service;

import com.polemon.api_lib.persistence.exception.PersistenceException;
import com.polemon.github_manager.client.GitHubClient;
import com.polemon.github_manager.exceptions.InvalidWorkflowException;
import com.polemon.github_manager.model.dto.InternalWorkflowDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowStageDTO;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.model.entity.InternalWorkflowEntity;
import com.polemon.github_manager.model.entity.InternalWorkflowStageEntity;
import com.polemon.github_manager.repository.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowServiceTest {

    private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    private final GitHubClient gitHubClient = mock(GitHubClient.class);
    private final AuthService authService = mock(AuthService.class);
    private final WorkflowService workflowService = new WorkflowService(workflowRepository, gitHubClient, authService);

    @Test
    void saveWorkflowStoresItForTheUser() {
        workflowService.saveWorkflow("u1", workflow("release"));

        ArgumentCaptor<InternalWorkflowEntity> created = ArgumentCaptor.forClass(InternalWorkflowEntity.class);
        verify(authService).ensureUser("u1");
        verify(workflowRepository).create(created.capture());
        assertNotNull(created.getValue().getId());
        assertEquals("u1", created.getValue().getUserId());
        assertEquals("release", created.getValue().getName());
        assertEquals("polemonLX", created.getValue().getStages().getFirst().getRepository().getOwner());
    }

    @Test
    void saveWorkflowRejectsANameTheUserAlreadyUses() {
        doThrow(new PersistenceException(PersistenceException.Reason.DUPLICATE, "duplicate", null))
                .when(workflowRepository).create(any());

        assertThrows(IllegalArgumentException.class, () -> workflowService.saveWorkflow("u1", workflow("release")));
    }

    @Test
    void saveWorkflowPropagatesOtherStorageFailures() {
        doThrow(new PersistenceException("down")).when(workflowRepository).create(any());

        assertThrows(PersistenceException.class, () -> workflowService.saveWorkflow("u1", workflow("release")));
    }

    @Test
    void saveWorkflowNeedsANameAndStages() {
        assertThrows(IllegalArgumentException.class, () -> workflowService.saveWorkflow("u1", new InternalWorkflowDTO(" ", List.of(stage()))));
        assertThrows(IllegalArgumentException.class, () -> workflowService.saveWorkflow("u1", new InternalWorkflowDTO("release", List.of())));
        verify(workflowRepository, never()).create(any());
    }

    @Test
    void getWorkflowsListsTheUsersWorkflows() {
        when(workflowRepository.findByUser("u1")).thenReturn(List.of(stored("w1", "release")));

        List<InternalWorkflowDTO> workflows = workflowService.getWorkflows("u1");

        assertEquals(1, workflows.size());
        assertEquals("release", workflows.getFirst().getName());
        assertEquals("auth", workflows.getFirst().getStages().getFirst().getRepository().getName());
        assertTrue(workflowService.getWorkflows("u2").isEmpty());
    }

    @Test
    void updateWorkflowReplacesTheStagesOfTheUsersWorkflow() {
        InternalWorkflowEntity stored = stored("w1", "release");
        stored.setStages(List.of());
        when(workflowRepository.findByUser("u1")).thenReturn(List.of(stored));

        workflowService.updateWorkflow("u1", workflow("release"));
        workflowService.updateWorkflow("u1", workflow("unknown"));

        assertEquals(1, stored.getStages().size());
        verify(workflowRepository).update(stored);
    }

    @Test
    void updateWorkflowNeedsAName() {
        assertThrows(IllegalArgumentException.class, () -> workflowService.updateWorkflow("u1", null));
        assertThrows(IllegalArgumentException.class, () -> workflowService.updateWorkflow("u1", new InternalWorkflowDTO(null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> workflowService.updateWorkflow("u1", new InternalWorkflowDTO(" ", List.of())));
    }

    @Test
    void deleteWorkflowDeletesOnlyTheUsersWorkflowWithThatName() {
        when(workflowRepository.findByUser("u1")).thenReturn(List.of(stored("w1", "release"), stored("w2", "deploy")));

        workflowService.deleteWorkflow("u1", "release");
        workflowService.deleteWorkflow("u1", "unknown");

        verify(workflowRepository).deleteById("w1");
        verify(workflowRepository, never()).deleteById("w2");
        assertThrows(IllegalArgumentException.class, () -> workflowService.deleteWorkflow("u1", null));
        assertThrows(IllegalArgumentException.class, () -> workflowService.deleteWorkflow("u1", " "));
    }

    @Test
    void startedWorkflowsAreTrackedPerUserUntilStopped() throws InvalidWorkflowException {
        when(workflowRepository.findByUser("u1")).thenReturn(List.of(stored("w1", "release")));

        workflowService.startWorkflow("u1", workflow("release"));

        assertEquals("release", workflowService.getInternalWorkflowActiveList("u1").getFirst().getWorkflowName());
        assertTrue(workflowService.getInternalWorkflowActiveList("u2").isEmpty());

        workflowService.stopWorkflow("u2", "release");
        assertEquals(1, workflowService.getInternalWorkflowActiveList("u1").size());

        workflowService.stopWorkflow("u1", "release");
        assertTrue(workflowService.getInternalWorkflowActiveList("u1").isEmpty());
    }

    @Test
    void startWorkflowNeedsAWorkflowTheUserHas() {
        when(workflowRepository.findByUser("u2")).thenReturn(List.of(stored("w1", "release")));

        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u1", workflow("release")));
        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u1", null));
        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u1", new InternalWorkflowDTO(null, List.of())));
        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u1", new InternalWorkflowDTO(" ", List.of())));
    }

    private static InternalWorkflowDTO workflow(String name) {
        return new InternalWorkflowDTO(name, List.of(stage()));
    }

    private static InternalWorkflowStageDTO stage() {
        InternalWorkflowStageDTO stage = new InternalWorkflowStageDTO();
        stage.setRepository(new RepositoryDTO("polemonLX", "auth"));
        stage.setTask(InternalWorkflowStageDTO.StageTask.WORKFLOW);
        stage.setWorkflowUrl("https://github.com/polemonLX/auth/actions/workflows/release.yml");
        return stage;
    }

    private static InternalWorkflowEntity stored(String id, String name) {
        InternalWorkflowStageEntity stage = new InternalWorkflowStageEntity();
        stage.setRepository(new InternalWorkflowStageEntity.RepositoryRef("polemonLX", "auth"));
        stage.setTask("WORKFLOW");
        InternalWorkflowEntity workflow = new InternalWorkflowEntity(name, List.of(stage));
        workflow.setId(id);
        workflow.setUserId("u1");
        return workflow;
    }

}
