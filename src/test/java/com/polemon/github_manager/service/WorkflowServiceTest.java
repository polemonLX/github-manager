package com.polemon.github_manager.service;

import com.polemon.api_lib.persistence.exception.PersistenceException;
import com.polemon.github_manager.client.GitHubClient;
import com.polemon.github_manager.exceptions.InvalidWorkflowException;
import com.polemon.github_manager.model.dto.InternalWorkflowActiveDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowStageActiveDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowStageDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowStageDTO.CommitOperation;
import com.polemon.github_manager.model.dto.InternalWorkflowStageDTO.RunMode;
import com.polemon.github_manager.model.dto.InternalWorkflowStageDTO.StageTask;
import com.polemon.github_manager.model.dto.InternalWorkflowStageDTO.VersionPart;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.model.dto.api.FileContentAPI;
import com.polemon.github_manager.model.dto.api.RateLimitAPI;
import com.polemon.github_manager.model.dto.api.WorkflowRunAPI;
import com.polemon.github_manager.model.entity.InternalWorkflowEntity;
import com.polemon.github_manager.model.entity.InternalWorkflowStageEntity;
import com.polemon.github_manager.repository.WorkflowRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class WorkflowServiceTest {

    private static final Instant START = Instant.parse("2026-11-11T10:00:00Z");

    private static final String URL = "https://github.com/polemonLX/auth/actions/workflows/release.yml";

    private static final WorkflowService.Pacing PACING = new WorkflowService.Pacing(
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2), 500, Duration.ofMinutes(5));

    private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    private final GitHubClient gitHubClient = mock(GitHubClient.class);
    private final AuthService authService = mock(AuthService.class);

    private final MovingClock clock = new MovingClock();
    private final List<Runnable> queued = new ArrayList<>();
    private final List<Duration> sleeps = new ArrayList<>();
    private Runnable onSleep = () -> {
    };

    private final WorkflowService workflowService = new WorkflowService(workflowRepository, gitHubClient, authService,
            PACING, queued::add, this::sleep, clock);

    @BeforeEach
    void setUp() {
        when(authService.getGithubToken("u1")).thenReturn("t1");
        when(gitHubClient.dispatchWorkflow(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(gitHubClient.updateFileContent(anyString(), anyString(), anyString(), any(), any(), anyString(), any(), anyString())).thenReturn(true);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

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
        assertThrows(IllegalArgumentException.class, () -> workflowService.saveWorkflow("u1", new InternalWorkflowDTO(" ", List.of(pipeline()))));
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
    void startWorkflowNeedsAWorkflowTheUserHasWithStages() {
        when(workflowRepository.findByUser("u2")).thenReturn(List.of(stored("w1", "release")));

        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u1", workflow("release")));
        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u1", null));
        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u1", new InternalWorkflowDTO(null, List.of())));
        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u1", new InternalWorkflowDTO(" ", List.of())));
        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u2", new InternalWorkflowDTO("release", null)));
        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u2", new InternalWorkflowDTO("release", List.of())));
        assertTrue(queued.isEmpty());
    }

    @Test
    void aStartedWorkflowWaitsUntilItsThreadRunsAndIsListedPerUser() throws InvalidWorkflowException {
        storedFor("u1", "release");

        workflowService.startWorkflow("u1", workflow("release"));

        InternalWorkflowActiveDTO run = only("u1");
        assertEquals("release", run.getWorkflowName());
        assertEquals("waiting", run.getStatus());
        assertTrue(run.isActive());
        assertEquals(1, run.getStageCount());
        assertNull(run.getStartedAtText());
        assertEquals("waiting", run.getStages().getFirst().getStatus());
        assertEquals(1, run.getNextStages().size());
        assertTrue(run.getCurrentStages().isEmpty());
        assertTrue(workflowService.getInternalWorkflowActiveList("u2").isEmpty());
        verifyNoInteractions(gitHubClient);
    }

    @Test
    void stagesRunInGroupsAndPipelinesAreFollowedUntilTheyComplete() throws InvalidWorkflowException {
        storedFor("u1", "release");
        InternalWorkflowStageDTO commit = commit(CommitOperation.SET_FILE_CONTENT, "VERSION");
        commit.setCommitContent("1.0.1");
        InternalWorkflowStageDTO release = pipeline();
        release.setRunMode(RunMode.WITH_PREVIOUS);
        InternalWorkflowStageDTO after = commit(CommitOperation.SET_FILE_CONTENT, "NEXT");
        after.setCommitContent("next");
        after.setRunMode(RunMode.AFTER_PREVIOUS_FINISHES);
        when(gitHubClient.findDispatchedRun(eq("polemonLX"), eq("auth"), eq("release.yml"), eq("main"), any(), eq("t1")))
                .thenReturn(run(7, "in_progress", null));
        when(gitHubClient.getRun("polemonLX", "auth", 7, "t1")).thenReturn(null, run(7, "completed", "success"));
        when(gitHubClient.getRateLimit("t1")).thenReturn(null, new RateLimitAPI(4000, 5000, START));

        workflowService.startWorkflow("u1", new InternalWorkflowDTO("release", List.of(commit, release, after)));
        runQueued();

        InOrder order = inOrder(gitHubClient);
        order.verify(gitHubClient).updateFileContent("polemonLX", "auth", "VERSION", null, "1.0.1", "Update VERSION", null, "t1");
        order.verify(gitHubClient).dispatchWorkflow("polemonLX", "auth", "release.yml", "main", "t1");
        order.verify(gitHubClient).findDispatchedRun("polemonLX", "auth", "release.yml", "main", START.minusSeconds(10), "t1");
        order.verify(gitHubClient, times(2)).getRun("polemonLX", "auth", 7, "t1");
        order.verify(gitHubClient).updateFileContent("polemonLX", "auth", "NEXT", null, "next", "Update NEXT", null, "t1");
        assertEquals(List.of(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30)), sleeps);

        InternalWorkflowActiveDTO run = only("u1");
        assertEquals("succeeded", run.getStatus());
        assertFalse(run.isActive());
        assertNull(run.getError());
        assertNotNull(run.getStartedAtText());
        assertNotNull(run.getFinishedAtText());
        assertEquals(List.of("succeeded", "succeeded", "succeeded"), run.getStages().stream().map(InternalWorkflowStageActiveDTO::getStatus).toList());
        assertEquals(List.of(false, true, false), run.getStages().stream().map(InternalWorkflowStageActiveDTO::isParallel).toList());
        assertEquals("https://github.com/polemonLX/auth/actions/runs/7", run.getStages().get(1).getRunUrl());
        assertEquals("polemonLX/auth - Pipeline", run.getStages().get(1).getTitle());
        assertNotNull(run.getStages().get(1).getStartedAt());
        assertTrue(run.getCurrentStages().isEmpty());
        assertTrue(run.getNextStages().isEmpty());
    }

    @Test
    void aFailedPipelineFailsTheRunStopsItsGroupAndSkipsWhatComesAfter() throws InvalidWorkflowException {
        storedFor("u1", "release");
        InternalWorkflowStageDTO first = pipeline();
        InternalWorkflowStageDTO parallel = pipeline();
        parallel.setWorkflowUrl("ci.yml");
        parallel.setTargetBranch("dev");
        parallel.setRunMode(RunMode.WITH_PREVIOUS);
        InternalWorkflowStageDTO after = commit(CommitOperation.SET_FILE_CONTENT, "NEXT");
        after.setRunMode(RunMode.AFTER_PREVIOUS_FINISHES);
        when(gitHubClient.findDispatchedRun(eq("polemonLX"), eq("auth"), eq("release.yml"), eq("main"), any(), eq("t1")))
                .thenReturn(run(7, "completed", "failure"));

        workflowService.startWorkflow("u1", new InternalWorkflowDTO("release", List.of(first, parallel, after)));
        runQueued();

        verify(gitHubClient).dispatchWorkflow("polemonLX", "auth", "ci.yml", "dev", "t1");
        verify(gitHubClient, never()).updateFileContent(anyString(), anyString(), anyString(), any(), any(), anyString(), any(), anyString());
        InternalWorkflowActiveDTO run = only("u1");
        assertEquals("failed", run.getStatus());
        assertEquals("Pipeline release.yml on polemonLX/auth ended with failure", run.getError());
        assertEquals(List.of("failed", "stopped", "waiting"), run.getStages().stream().map(InternalWorkflowStageActiveDTO::getStatus).toList());
        assertEquals("Pipeline release.yml on polemonLX/auth ended with failure", run.getStages().getFirst().getError());
    }

    @Test
    void aRunThatNeverShowsUpFailsAfterTheLookupTimeoutCheckingSlowlyWhenCallsRunLow() throws InvalidWorkflowException {
        storedFor("u1", "release");
        when(gitHubClient.getRateLimit("t1")).thenReturn(new RateLimitAPI(100, 5000, START));

        workflowService.startWorkflow("u1", workflow("release"));
        runQueued();

        assertEquals(List.of(Duration.ofSeconds(10), Duration.ofMinutes(2), Duration.ofMinutes(2), Duration.ofMinutes(2)), sleeps);
        verify(gitHubClient, times(4)).findDispatchedRun(anyString(), anyString(), anyString(), anyString(), any(), anyString());
        InternalWorkflowActiveDTO run = only("u1");
        assertEquals("failed", run.getStatus());
        assertEquals("No run of release.yml showed up on polemonLX/auth", run.getError());
    }

    @Test
    void stoppingWhileFollowingAPipelineStopsTheRun() throws InvalidWorkflowException {
        storedFor("u1", "release");
        when(gitHubClient.findDispatchedRun(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(run(7, "queued", null));
        onSleep = () -> {
            if (sleeps.size() == 2)
                workflowService.stopWorkflow("u1", "release");
        };

        workflowService.startWorkflow("u1", workflow("release"));
        runQueued();

        InternalWorkflowActiveDTO run = only("u1");
        assertEquals("stopped", run.getStatus());
        assertEquals("Stopped by the user", run.getError());
        assertEquals("stopped", run.getStages().getFirst().getStatus());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void stoppingBetweenStagesStopsBeforeTheNextOne() throws InvalidWorkflowException {
        storedFor("u1", "release");
        InternalWorkflowStageDTO first = commit(CommitOperation.SET_FILE_CONTENT, "A");
        InternalWorkflowStageDTO second = commit(CommitOperation.SET_FILE_CONTENT, "B");
        second.setRunMode(RunMode.WITH_PREVIOUS);
        when(gitHubClient.updateFileContent(anyString(), anyString(), eq("A"), any(), any(), anyString(), any(), anyString())).thenAnswer(call -> {
            workflowService.stopWorkflow("u1", "release");
            Thread.interrupted();
            return true;
        });

        workflowService.startWorkflow("u1", new InternalWorkflowDTO("release", List.of(first, second)));
        runQueued();

        InternalWorkflowActiveDTO run = only("u1");
        assertEquals("stopped", run.getStatus());
        assertEquals(List.of("succeeded", "waiting"), run.getStages().stream().map(InternalWorkflowStageActiveDTO::getStatus).toList());
    }

    @Test
    void aWorkflowStoppedBeforeItsThreadRunsNeverStarts() throws InvalidWorkflowException {
        storedFor("u1", "release");

        workflowService.startWorkflow("u1", workflow("release"));
        workflowService.stopWorkflow("u1", "release");
        runQueued();

        assertEquals("stopped", only("u1").getStatus());
        verifyNoInteractions(gitHubClient);
    }

    @Test
    void aRunningWorkflowCantStartAgainNorBeDismissedButAnEndedOneCan() throws InvalidWorkflowException {
        storedFor("u1", "release", "deploy");
        workflowService.startWorkflow("u1", workflow("release"));
        workflowService.startWorkflow("u1", workflow("deploy"));

        assertThrows(InvalidWorkflowException.class, () -> workflowService.startWorkflow("u1", workflow("release")));
        assertFalse(workflowService.dismissWorkflow("u1", "release"));
        assertFalse(workflowService.dismissWorkflow("u1", "unknown"));
        workflowService.stopWorkflow("u1", "unknown");

        runQueued();
        workflowService.stopWorkflow("u1", "release");
        assertEquals("failed", only("u1", "release").getStatus());

        workflowService.startWorkflow("u1", workflow("release"));
        assertEquals(List.of("deploy", "release"), workflowService.getInternalWorkflowActiveList("u1").stream()
                .map(InternalWorkflowActiveDTO::getWorkflowName).toList());
        runQueued();

        assertTrue(workflowService.dismissWorkflow("u1", "release"));
        assertEquals(List.of("deploy"), workflowService.getInternalWorkflowActiveList("u1").stream()
                .map(InternalWorkflowActiveDTO::getWorkflowName).toList());
    }

    @Test
    void aStageWithoutATaskFailsTheRun() throws InvalidWorkflowException {
        InternalWorkflowStageDTO stage = new InternalWorkflowStageDTO();
        stage.setRepository(new RepositoryDTO("polemonLX", "auth"));

        InternalWorkflowActiveDTO run = runOne(stage);

        assertEquals("failed", run.getStatus());
        assertEquals("Stage 0 has no task", run.getError());
        assertEquals("polemonLX/auth", run.getStages().getFirst().getTitle());
    }

    @Test
    void pipelineStagesNeedARepositoryAWorkflowFileAndAnAcceptedDispatch() throws InvalidWorkflowException {
        InternalWorkflowStageDTO noRepository = pipeline();
        noRepository.setRepository(null);
        assertEquals("Pipeline stage has no repository", runOne(noRepository).getError());

        InternalWorkflowStageDTO noUrl = pipeline();
        noUrl.setWorkflowUrl(" ");
        assertEquals("Pipeline stage has no workflow URL", runOne(noUrl).getError());

        InternalWorkflowStageDTO nullUrl = pipeline();
        nullUrl.setWorkflowUrl(null);
        assertEquals("Pipeline stage has no workflow URL", runOne(nullUrl).getError());

        InternalWorkflowStageDTO folder = pipeline();
        folder.setWorkflowUrl("https://github.com/polemonLX/auth/actions/");
        assertEquals("Cannot extract workflow file name from: https://github.com/polemonLX/auth/actions/", runOne(folder).getError());

        when(gitHubClient.dispatchWorkflow(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        InternalWorkflowStageDTO refused = pipeline();
        refused.setTargetBranch(" ");
        assertEquals("Failed to dispatch pipeline release.yml on polemonLX/auth", runOne(refused).getError());
        verify(gitHubClient).dispatchWorkflow("polemonLX", "auth", "release.yml", "main", "t1");
    }

    @Test
    void commitStagesNeedARepositoryAnOperationAndAFile() throws InvalidWorkflowException {
        InternalWorkflowStageDTO noRepository = commit(CommitOperation.SET_FILE_CONTENT, "A");
        noRepository.setRepository(null);
        assertEquals("Commit stage has no repository", runOne(noRepository).getError());

        assertEquals("Commit stage has no commit operation", runOne(commit(null, "A")).getError());
        assertEquals("Commit stage has no file path", runOne(commit(CommitOperation.SET_FILE_CONTENT, null)).getError());
        assertEquals("Commit stage has no file path", runOne(commit(CommitOperation.FIND_AND_REPLACE_REGEX, " ")).getError());
        assertEquals("File not found: pom.xml in polemonLX/auth", runOne(commit(CommitOperation.BUMP_MAVEN_VERSION, null)).getError());
        assertEquals("File not found: A in polemonLX/auth", runOne(commit(CommitOperation.FIND_AND_REPLACE_REGEX, "A")).getError());

        when(gitHubClient.updateFileContent(anyString(), anyString(), anyString(), any(), any(), anyString(), any(), anyString())).thenReturn(false);
        assertEquals("Failed to commit A to polemonLX/auth", runOne(commit(CommitOperation.SET_FILE_CONTENT, "A")).getError());
    }

    @Test
    void commitStagesChangeTheFileOnTheTargetBranchWithTheGivenMessage() throws InvalidWorkflowException {
        when(gitHubClient.getFileContent("polemonLX", "auth", "README.md", "dev", "t1"))
                .thenReturn(new FileContentAPI("README.md", "sha1", "version 1.0 and 1.0"));
        InternalWorkflowStageDTO replace = commit(CommitOperation.FIND_AND_REPLACE_REGEX, "README.md");
        replace.setTargetBranch("dev");
        replace.setCommitFindRegex("1\\.0");
        replace.setCommitContent("2.0");
        replace.setCommitMessage("Release 2.0");

        assertEquals("succeeded", runOne(replace).getStatus());

        verify(gitHubClient).updateFileContent("polemonLX", "auth", "README.md", "dev", "version 2.0 and 2.0", "Release 2.0", "sha1", "t1");
    }

    @Test
    void mavenBumpsChangeTheProjectVersionNotTheParent() throws InvalidWorkflowException {
        String pom = "<project><parent><version>9.9.9</version></parent><version>1.2.3-SNAPSHOT</version></project>";
        when(gitHubClient.getFileContent("polemonLX", "auth", "pom.xml", null, "t1")).thenReturn(new FileContentAPI("pom.xml", "sha", pom));

        for (VersionPart part : new VersionPart[]{null, VersionPart.PATCH, VersionPart.MINOR, VersionPart.MAJOR}) {
            InternalWorkflowStageDTO bump = commit(CommitOperation.BUMP_MAVEN_VERSION, null);
            bump.setVersionPart(part);
            assertEquals("succeeded", runOne(bump).getStatus(), String.valueOf(part));
        }

        verify(gitHubClient, times(2)).updateFileContent(eq("polemonLX"), eq("auth"), eq("pom.xml"), isNull(),
                eq("<project><parent><version>9.9.9</version></parent><version>1.2.4-SNAPSHOT</version></project>"), anyString(), eq("sha"), eq("t1"));
        verify(gitHubClient).updateFileContent(eq("polemonLX"), eq("auth"), eq("pom.xml"), isNull(),
                eq("<project><parent><version>9.9.9</version></parent><version>1.3.0-SNAPSHOT</version></project>"), anyString(), eq("sha"), eq("t1"));
        verify(gitHubClient).updateFileContent(eq("polemonLX"), eq("auth"), eq("pom.xml"), isNull(),
                eq("<project><parent><version>9.9.9</version></parent><version>2.0.0-SNAPSHOT</version></project>"), anyString(), eq("sha"), eq("t1"));
    }

    @Test
    void aMavenBumpWithoutAProjectVersionFails() throws InvalidWorkflowException {
        when(gitHubClient.getFileContent(anyString(), anyString(), eq("pom.xml"), any(), anyString()))
                .thenReturn(new FileContentAPI("pom.xml", "sha", "<project><parent><version>1.0.0</version></parent></project>"));
        InternalWorkflowStageDTO bump = commit(CommitOperation.BUMP_MAVEN_VERSION, " ");

        assertEquals("No Maven version found to bump", runOne(bump).getError());

        when(gitHubClient.getFileContent(anyString(), anyString(), eq("pom.xml"), any(), anyString()))
                .thenReturn(new FileContentAPI("pom.xml", "sha", "<project><version>x</version></project>"));
        assertEquals("No Maven version found to bump", runOne(commit(CommitOperation.BUMP_MAVEN_VERSION, null)).getError());
    }

    @Test
    void stageDetailsDescribeWhatEachStageDoes() throws InvalidWorkflowException {
        storedFor("u1", "release");
        InternalWorkflowStageDTO bump = commit(CommitOperation.BUMP_MAVEN_VERSION, null);
        bump.setTargetBranch("dev");
        InternalWorkflowStageDTO replace = commit(CommitOperation.FIND_AND_REPLACE_REGEX, "README.md");
        replace.setCommitFindRegex("a+");
        InternalWorkflowStageDTO set = commit(CommitOperation.SET_FILE_CONTENT, "VERSION");
        InternalWorkflowStageDTO pipeline = pipeline();
        pipeline.setTargetBranch("");
        InternalWorkflowStageDTO bare = commit(null, null);
        bare.setRepository(null);
        bare.setTargetBranch("main");
        InternalWorkflowStageDTO empty = new InternalWorkflowStageDTO();

        workflowService.startWorkflow("u1", new InternalWorkflowDTO("release", List.of(bump, replace, set, pipeline, bare, empty)));

        List<InternalWorkflowStageActiveDTO> stages = only("u1").getStages();
        assertEquals(List.of("bump Maven version on dev", "replace /a+/ in README.md", "set content of VERSION",
                "pipeline: " + URL, "on main", ""), stages.stream().map(InternalWorkflowStageActiveDTO::getDetails).toList());
        assertEquals(List.of("polemonLX/auth - Commit", "polemonLX/auth - Commit", "polemonLX/auth - Commit",
                "polemonLX/auth - Pipeline", "Commit", ""), stages.stream().map(InternalWorkflowStageActiveDTO::getTitle).toList());
    }

    @Test
    void theFirstStageIsNeverParallel() throws InvalidWorkflowException {
        storedFor("u1", "release");
        InternalWorkflowStageDTO first = pipeline();
        first.setRunMode(RunMode.WITH_PREVIOUS);

        workflowService.startWorkflow("u1", new InternalWorkflowDTO("release", List.of(first)));

        assertFalse(only("u1").getStages().getFirst().isParallel());
    }

    @Test
    void theServiceRunsWorkflowsOnTheirOwnThreads() throws Exception {
        WorkflowService service = new WorkflowService(workflowRepository, gitHubClient, authService,
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2), 500, Duration.ofMinutes(5));
        storedFor("u1", "release");

        service.startWorkflow("u1", new InternalWorkflowDTO("release", List.of(commit(CommitOperation.SET_FILE_CONTENT, "A"))));

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (service.getInternalWorkflowActiveList("u1").getFirst().isActive() && System.nanoTime() < deadline)
            Thread.sleep(10);
        assertEquals("succeeded", service.getInternalWorkflowActiveList("u1").getFirst().getStatus());
    }

    private void sleep(Duration duration) throws InterruptedException {
        sleeps.add(duration);
        clock.now = clock.now.plus(duration);
        onSleep.run();
        if (Thread.interrupted())
            throw new InterruptedException();
    }

    private void runQueued() {
        List<Runnable> tasks = new ArrayList<>(queued);
        queued.clear();
        tasks.forEach(Runnable::run);
    }

    private InternalWorkflowActiveDTO runOne(InternalWorkflowStageDTO stage) throws InvalidWorkflowException {
        storedFor("u1", "single");
        workflowService.dismissWorkflow("u1", "single");
        workflowService.startWorkflow("u1", new InternalWorkflowDTO("single", List.of(stage)));
        runQueued();
        return only("u1", "single");
    }

    private InternalWorkflowActiveDTO only(String userId) {
        List<InternalWorkflowActiveDTO> runs = workflowService.getInternalWorkflowActiveList(userId);
        assertEquals(1, runs.size());
        return runs.getFirst();
    }

    private InternalWorkflowActiveDTO only(String userId, String name) {
        return workflowService.getInternalWorkflowActiveList(userId).stream()
                .filter(run -> run.getWorkflowName().equals(name))
                .findFirst().orElseThrow();
    }

    private void storedFor(String userId, String... names) {
        List<InternalWorkflowEntity> workflows = new ArrayList<>();
        for (String name : names)
            workflows.add(stored("w-" + name, name));
        when(workflowRepository.findByUser(userId)).thenReturn(workflows);
    }

    private static WorkflowRunAPI run(long id, String status, String conclusion) {
        return new WorkflowRunAPI(id, 1, "Release", status, conclusion, "main", "workflow_dispatch",
                "https://github.com/polemonLX/auth/actions/runs/" + id, START);
    }

    private static InternalWorkflowDTO workflow(String name) {
        return new InternalWorkflowDTO(name, List.of(pipeline()));
    }

    private static InternalWorkflowStageDTO pipeline() {
        InternalWorkflowStageDTO stage = new InternalWorkflowStageDTO();
        stage.setRepository(new RepositoryDTO("polemonLX", "auth"));
        stage.setTask(StageTask.WORKFLOW);
        stage.setWorkflowUrl(URL);
        return stage;
    }

    private static InternalWorkflowStageDTO commit(CommitOperation operation, String file) {
        InternalWorkflowStageDTO stage = new InternalWorkflowStageDTO();
        stage.setRepository(new RepositoryDTO("polemonLX", "auth"));
        stage.setTask(StageTask.COMMIT);
        stage.setCommitOperation(operation);
        stage.setCommitFilePath(file);
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

    private static final class MovingClock extends Clock {

        private Instant now = START;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

    }

}
