package com.polemon.github_manager.service;

import com.polemon.api_lib.persistence.exception.PersistenceException;
import com.polemon.github_manager.client.GitHubClient;
import com.polemon.github_manager.exceptions.InvalidWorkflowException;
import com.polemon.github_manager.model.domain.InternalWorkflowActiveDomain;
import com.polemon.github_manager.model.domain.InternalWorkflowActiveDomain.WorkflowStatus;
import com.polemon.github_manager.model.domain.InternalWorkflowStageActiveDomain;
import com.polemon.github_manager.model.domain.InternalWorkflowStageActiveDomain.StageStatus;
import com.polemon.github_manager.model.domain.RepositoryDomain;
import com.polemon.github_manager.model.dto.*;
import com.polemon.github_manager.model.dto.api.FileContentAPI;
import com.polemon.github_manager.model.dto.api.RateLimitAPI;
import com.polemon.github_manager.model.dto.api.WorkflowRunAPI;
import com.polemon.github_manager.model.entity.InternalWorkflowEntity;
import com.polemon.github_manager.model.entity.InternalWorkflowStageEntity;
import com.polemon.github_manager.repository.WorkflowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service class for managing each user's internal workflows.
 * This service provides methods to retrieve and persist internal workflows,
 * delegating storage to the {@link WorkflowRepository}, and runs them with the
 * GitHub token of the user who started them.
 *
 * <p>A run goes through its stages in groups (see
 * {@link InternalWorkflowActiveDomain}): commits are made right away, and
 * pipelines are dispatched and then followed on GitHub until their runs
 * complete. Following a run costs API calls from the user's 5000 per hour, so
 * GitHub is asked at most once per {@code github-manager.workflow.poll-interval}
 * (30 s by default, about 120 calls per hour per pipeline), and only once per
 * {@code slow-poll-interval} when fewer than {@code low-rate-limit} calls are
 * left. The last run of each workflow stays listed, ended or not, until it is
 * started again or dismissed.
 */
@Service
@Slf4j
public class WorkflowService {

    /**
     * Ref used when dispatching a pipeline whose stage does not specify a target branch.
     */
    private static final String DEFAULT_BRANCH = "main";

    /**
     * Default file acted on by a Maven version bump when the stage does not specify one.
     */
    private static final String DEFAULT_POM_PATH = "pom.xml";

    /**
     * Matches a three-part Maven version tag, capturing major, minor, patch and
     * any qualifier suffix (e.g. {@code -SNAPSHOT}).
     */
    private static final Pattern MAVEN_VERSION_PATTERN =
            Pattern.compile("<version>\\s*(\\d+)\\.(\\d+)\\.(\\d+)([^<]*)</version>");

    /**
     * How much earlier than the dispatch a run may say it was created, since
     * GitHub's clock and ours differ slightly.
     */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(10);

    /**
     * Format of the run times shown on the dashboard.
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy").withZone(ZoneId.systemDefault());

    /**
     * Store for persisting and retrieving internal workflows.
     */
    private final WorkflowRepository workflowRepository;

    /**
     * Client for the GitHub API calls performed by workflow stages
     * (commits, pipeline dispatches and following their runs).
     */
    private final GitHubClient gitHubClient;

    /**
     * Service used to retrieve each user's GitHub token for API calls.
     */
    private final AuthService authService;

    /**
     * How often GitHub is asked about the pipelines being followed.
     */
    private final Pacing pacing;

    /**
     * Runs each workflow on its own thread.
     */
    private final Executor executor;

    /**
     * Waits between the checks on GitHub.
     */
    private final Sleeper sleeper;

    /**
     * Tells the time, for the stage and run instants.
     */
    private final Clock clock;

    /**
     * The last run of each workflow per user id, keyed by workflow name, in
     * start order.
     */
    private final Map<String, Map<String, Worker>> internalWorkflowActiveDomainMap;

    @Autowired
    public WorkflowService(WorkflowRepository workflowRepository, GitHubClient gitHubClient, AuthService authService,
                           @Value("${github-manager.workflow.first-check:10s}") Duration firstCheck,
                           @Value("${github-manager.workflow.poll-interval:30s}") Duration pollInterval,
                           @Value("${github-manager.workflow.slow-poll-interval:2m}") Duration slowPollInterval,
                           @Value("${github-manager.workflow.low-rate-limit:500}") int lowRateLimit,
                           @Value("${github-manager.workflow.run-lookup-timeout:5m}") Duration runLookupTimeout) {
        this(workflowRepository, gitHubClient, authService,
                new Pacing(firstCheck, pollInterval, slowPollInterval, lowRateLimit, runLookupTimeout),
                task -> Thread.ofVirtual().name("workflow").start(task), Thread::sleep, Clock.systemUTC());
    }

    WorkflowService(WorkflowRepository workflowRepository, GitHubClient gitHubClient, AuthService authService,
                    Pacing pacing, Executor executor, Sleeper sleeper, Clock clock) {
        this.workflowRepository = workflowRepository;
        this.gitHubClient = gitHubClient;
        this.authService = authService;
        this.pacing = pacing;
        this.executor = executor;
        this.sleeper = sleeper;
        this.clock = clock;

        this.internalWorkflowActiveDomainMap = new ConcurrentHashMap<>();
        log.info("Pipelines checked every {} ({} below {} API calls left)", pacing.interval(), pacing.slowInterval(), pacing.lowRateLimit());
    }

    /**
     * Validates and persists a new internal workflow for a user.
     *
     * @param userId              the user id
     * @param internalWorkflowDTO the workflow to save
     * @throws IllegalArgumentException if the workflow name is missing or already used by the user, or it has no stages
     */
    public void saveWorkflow(String userId, InternalWorkflowDTO internalWorkflowDTO) {
        if (internalWorkflowDTO.getName() == null || internalWorkflowDTO.getName().trim().isEmpty())
            throw new IllegalArgumentException("Workflow name must not be empty");

        if (internalWorkflowDTO.getStages() == null || internalWorkflowDTO.getStages().isEmpty())
            throw new IllegalArgumentException("Workflow must have at least one stage");

        InternalWorkflowEntity internalWorkflowEntity = convertDtoToEntity(internalWorkflowDTO);
        internalWorkflowEntity.setId(UUID.randomUUID().toString());
        internalWorkflowEntity.setUserId(userId);

        authService.ensureUser(userId);
        try {
            workflowRepository.create(internalWorkflowEntity);
        } catch (PersistenceException e) {
            if (e.getReason() == PersistenceException.Reason.DUPLICATE)
                throw new IllegalArgumentException("A workflow named '" + internalWorkflowDTO.getName() + "' already exists");
            throw e;
        }
    }

    /**
     * Retrieves a user's persisted internal workflows.
     *
     * @param userId the user id
     * @return the list of internal workflows
     */
    public List<InternalWorkflowDTO> getWorkflows(String userId) {
        List<InternalWorkflowEntity> internalWorkflowEntities = workflowRepository.findByUser(userId);
        return internalWorkflowEntities.stream().map(this::convertEntityToDto).toList();
    }

    /**
     * Replaces the stages of the user's stored workflow that shares the given
     * workflow's name. If none matches, nothing is persisted.
     *
     * @param userId          the user id
     * @param updatedWorkflow the workflow to persist in place of the existing one
     * @throws IllegalArgumentException if the workflow is {@code null} or its name is missing
     */
    public void updateWorkflow(String userId, InternalWorkflowDTO updatedWorkflow) {
        if (updatedWorkflow == null || updatedWorkflow.getName() == null || updatedWorkflow.getName().trim().isEmpty())
            throw new IllegalArgumentException("Workflow name must not be empty");

        Optional<InternalWorkflowEntity> stored = findByName(userId, updatedWorkflow.getName());
        if (stored.isEmpty()) {
            log.warn("No workflow named '{}' found to update", updatedWorkflow.getName());
            return;
        }

        stored.get().setStages(convertDtoToEntity(updatedWorkflow).getStages());
        workflowRepository.update(stored.get());
        log.debug("Updated workflow '{}'", updatedWorkflow.getName());
    }

    /**
     * Deletes the user's stored workflow with the given name. If none matches,
     * nothing is changed.
     *
     * @param userId       the user id
     * @param workflowName the name of the workflow to delete
     * @throws IllegalArgumentException if the name is {@code null} or empty
     */
    public void deleteWorkflow(String userId, String workflowName) {
        if (workflowName == null || workflowName.trim().isEmpty())
            throw new IllegalArgumentException("Workflow name must not be empty");

        Optional<InternalWorkflowEntity> stored = findByName(userId, workflowName);
        if (stored.isEmpty()) {
            log.warn("No workflow named '{}' found to delete", workflowName);
            return;
        }

        workflowRepository.deleteById(stored.get().getId());
        log.debug("Deleted workflow '{}'", workflowName);
    }

    /**
     * Retrieves the last run of each of a user's workflows, active or ended.
     *
     * @param userId the user id
     * @return the runs, in start order
     */
    public List<InternalWorkflowActiveDTO> getInternalWorkflowActiveList(String userId) {
        Map<String, Worker> active = activeWorkflowsOf(userId);
        synchronized (active) {
            return active.values().stream()
                    .map(worker -> convertDomainToDto(worker.domain))
                    .toList();
        }
    }

    /**
     * Starts a workflow from the submitted definition, including the per-stage
     * runtime values entered in the start dialog. The workflow must exist among
     * the user's stored workflows; the submitted stages (with any start-time
     * overrides) drive this run without changing what is persisted. The run
     * goes on in the background and replaces the previous run of the same
     * workflow on the dashboard.
     *
     * @param userId   the user id
     * @param workflow the workflow name and its stages, as submitted by the start dialog
     * @throws InvalidWorkflowException if the workflow is empty, the user has no workflow with the given name,
     *                                  or that workflow is still running
     */
    public void startWorkflow(String userId, InternalWorkflowDTO workflow) throws InvalidWorkflowException {
        if (workflow == null || workflow.getName() == null || workflow.getName().trim().isEmpty())
            throw new InvalidWorkflowException("Workflow name must not be empty");
        if (workflow.getStages() == null || workflow.getStages().isEmpty())
            throw new InvalidWorkflowException("Workflow must have at least one stage");

        String workflowName = workflow.getName();
        if (findByName(userId, workflowName).isEmpty())
            throw new InvalidWorkflowException("Internal workflow not found: " + workflowName);

        Worker worker = new Worker(userId, convertEntityToDomain(convertDtoToEntity(workflow)));
        Map<String, Worker> active = activeWorkflowsOf(userId);
        synchronized (active) {
            Worker previous = active.get(workflowName);
            if (previous != null && previous.domain.isActive())
                throw new InvalidWorkflowException("Workflow '" + workflowName + "' is already running");
            active.remove(workflowName);
            active.put(workflowName, worker);
        }
        log.info("Starting workflow '{}' ({} stage(s))", workflowName, workflow.getStages().size());
        executor.execute(worker::run);
    }

    /**
     * Stops the user's workflow with the given name, if it is running. The run
     * stays listed as stopped; pipelines already dispatched go on on GitHub.
     *
     * @param userId       the user id
     * @param workflowName the name of the workflow to stop
     */
    public void stopWorkflow(String userId, String workflowName) {
        Worker worker = activeWorkflowsOf(userId).get(workflowName);
        if (worker != null && worker.domain.isActive())
            worker.stop();
    }

    /**
     * Removes the user's ended run of a workflow from the list. A run that is
     * still active stays (stop it first).
     *
     * @param userId       the user id
     * @param workflowName the workflow name
     * @return {@code true} if a run was removed
     */
    public boolean dismissWorkflow(String userId, String workflowName) {
        Map<String, Worker> active = activeWorkflowsOf(userId);
        synchronized (active) {
            Worker worker = active.get(workflowName);
            if (worker == null || worker.domain.isActive())
                return false;
            active.remove(workflowName);
            return true;
        }
    }

    /**
     * Finds one of a user's stored workflows by name.
     *
     * @param userId       the user id
     * @param workflowName the workflow name
     * @return the workflow, or empty if the user has none with that name
     */
    private Optional<InternalWorkflowEntity> findByName(String userId, String workflowName) {
        return workflowRepository.findByUser(userId).stream()
                .filter(workflow -> workflow.getName().equals(workflowName))
                .findFirst();
    }

    /**
     * The workflow runs of a user, created empty on first use.
     *
     * @param userId the user id
     * @return the user's runs, keyed by workflow name, in start order
     */
    private Map<String, Worker> activeWorkflowsOf(String userId) {
        return internalWorkflowActiveDomainMap.computeIfAbsent(userId, id -> Collections.synchronizedMap(new LinkedHashMap<>()));
    }

    /**
     * Executes a commit stage: reads the target file, applies the stage's commit
     * operation to its content and commits the result back through the GitHub
     * contents API. Setting the content of a missing file creates it (a commit
     * without a SHA).
     *
     * @param stage the commit stage to execute
     * @param token the GitHub token to authenticate with
     * @throws IllegalStateException if the stage is incomplete, the file cannot
     *                               be read, or the commit is rejected
     */
    private void executeCommitStage(InternalWorkflowStageActiveDomain stage, String token) {
        RepositoryDomain repository = stage.getRepository();
        if (repository == null)
            throw new IllegalStateException("Commit stage has no repository");
        if (stage.getCommitOperation() == null)
            throw new IllegalStateException("Commit stage has no commit operation");

        String owner = repository.getOwner();
        String name = repository.getName();
        String branch = stage.getTargetBranch();

        String filePath = stage.getCommitFilePath();
        if (filePath == null || filePath.trim().isEmpty()) {
            if (stage.getCommitOperation() == InternalWorkflowStageActiveDomain.CommitOperation.BUMP_MAVEN_VERSION)
                filePath = DEFAULT_POM_PATH;
            else
                throw new IllegalStateException("Commit stage has no file path");
        }

        FileContentAPI file = gitHubClient.getFileContent(owner, name, filePath, branch, token);

        String newContent;
        switch (stage.getCommitOperation()) {
            case BUMP_MAVEN_VERSION -> {
                if (file == null)
                    throw new IllegalStateException("File not found: " + filePath + " in " + owner + "/" + name);
                newContent = bumpMavenVersion(file.content(), stage.getVersionPart());
            }
            case FIND_AND_REPLACE_REGEX -> {
                if (file == null)
                    throw new IllegalStateException("File not found: " + filePath + " in " + owner + "/" + name);
                newContent = file.content().replaceAll(stage.getCommitFindRegex(), stage.getCommitContent());
            }
            default -> newContent = stage.getCommitContent();
        }

        String message = stage.getCommitMessage();
        if (message == null || message.trim().isEmpty())
            message = "Update " + filePath;

        boolean committed = gitHubClient.updateFileContent(owner, name, filePath, branch, newContent, message, file == null ? null : file.sha(), token);
        if (!committed)
            throw new IllegalStateException("Failed to commit " + filePath + " to " + owner + "/" + name);
    }

    /**
     * Executes a pipeline stage by dispatching a {@code workflow_dispatch} event
     * for the stage's workflow file on its target branch.
     *
     * @param stage the pipeline stage to execute
     * @param token the GitHub token to authenticate with
     * @throws IllegalStateException if the stage is incomplete or the dispatch is rejected
     */
    private void executeWorkflowStage(InternalWorkflowStageActiveDomain stage, String token) {
        RepositoryDomain repository = stage.getRepository();
        if (repository == null)
            throw new IllegalStateException("Pipeline stage has no repository");

        String workflowFileName = extractWorkflowFileName(stage.getWorkflowUrl());
        if (!gitHubClient.dispatchWorkflow(repository.getOwner(), repository.getName(), workflowFileName, refOf(stage), token))
            throw new IllegalStateException("Failed to dispatch pipeline " + workflowFileName + " on " + repository.getOwner() + "/" + repository.getName());
    }

    /**
     * Asks GitHub how a dispatched pipeline is doing: first which run the
     * dispatch created, then that run's state. One API call.
     *
     * @param stage the pipeline stage, running
     * @param token the GitHub token to authenticate with
     * @return {@code true} once its run succeeded; {@code false} while it is
     *         queued or running, not listed yet, or GitHub didn't answer
     * @throws IllegalStateException if the run ended without success, or no run
     *                               showed up within the lookup timeout
     */
    private boolean checkPipeline(InternalWorkflowStageActiveDomain stage, String token) {
        String owner = stage.getRepository().getOwner();
        String name = stage.getRepository().getName();
        String workflowFileName = extractWorkflowFileName(stage.getWorkflowUrl());

        WorkflowRunAPI run;
        if (stage.getRunId() == null) {
            run = gitHubClient.findDispatchedRun(owner, name, workflowFileName, refOf(stage), stage.getStartedAt().minus(CLOCK_SKEW), token);
            if (run == null) {
                if (Duration.between(stage.getStartedAt(), clock.instant()).compareTo(pacing.runLookupTimeout()) > 0)
                    throw new IllegalStateException("No run of " + workflowFileName + " showed up on " + owner + "/" + name);
                return false;
            }
            stage.setRunId(run.getId());
            stage.setRunUrl(run.getUrl());
            log.debug("Pipeline {} on {}/{} is run {}", workflowFileName, owner, name, run.getId());
        } else {
            run = gitHubClient.getRun(owner, name, stage.getRunId(), token);
            if (run == null)
                return false;
        }

        if (!"completed".equals(run.getStatus()))
            return false;
        if (!"success".equals(run.getConclusion()))
            throw new IllegalStateException("Pipeline " + workflowFileName + " on " + owner + "/" + name + " ended with " + run.getConclusion());
        return true;
    }

    /**
     * How long to wait before the next check on GitHub: the usual interval,
     * or the slow one when the user's API calls run low. Reading the rate
     * limit costs no call.
     *
     * @param token the GitHub token the checks use
     * @return the wait
     */
    private Duration nextCheckIn(String token) {
        RateLimitAPI rateLimit = gitHubClient.getRateLimit(token);
        if (rateLimit != null && rateLimit.remaining() < pacing.lowRateLimit()) {
            log.debug("{} GitHub API calls left, checking pipelines every {}", rateLimit.remaining(), pacing.slowInterval());
            return pacing.slowInterval();
        }
        return pacing.interval();
    }

    /**
     * Converts a workflow run to its DTO representation.
     *
     * @param workflowActiveDomain the run to convert
     * @return the corresponding DTO
     */
    private InternalWorkflowActiveDTO convertDomainToDto(InternalWorkflowActiveDomain workflowActiveDomain) {
        InternalWorkflowActiveDTO internalWorkflowActiveDTO = new InternalWorkflowActiveDTO();
        internalWorkflowActiveDTO.setWorkflowName(workflowActiveDomain.getWorkflowName());
        internalWorkflowActiveDTO.setStatus(workflowActiveDomain.getStatus().name().toLowerCase(Locale.ROOT));
        internalWorkflowActiveDTO.setActive(workflowActiveDomain.isActive());
        internalWorkflowActiveDTO.setStageCount(workflowActiveDomain.getStages().size());
        internalWorkflowActiveDTO.setStages(convertStageDomainToDto(workflowActiveDomain.getStages()));
        internalWorkflowActiveDTO.setCurrentStages(convertStageDomainToDto(workflowActiveDomain.getCurrentStages()));
        internalWorkflowActiveDTO.setNextStages(convertStageDomainToDto(workflowActiveDomain.getNextStages()));
        internalWorkflowActiveDTO.setStartedAt(workflowActiveDomain.getStartedAt());
        internalWorkflowActiveDTO.setStartedAtText(format(workflowActiveDomain.getStartedAt()));
        internalWorkflowActiveDTO.setFinishedAtText(format(workflowActiveDomain.getFinishedAt()));
        internalWorkflowActiveDTO.setError(workflowActiveDomain.getError());
        internalWorkflowActiveDTO.setExpectedCompletionTimeSeconds(workflowActiveDomain.getExpectedCompletionTimeSeconds());
        return internalWorkflowActiveDTO;
    }

    /**
     * Converts a persisted {@link InternalWorkflowEntity} to a runtime domain object.
     *
     * @param internalWorkflowEntity the entity to convert
     * @return the corresponding domain object
     */
    private InternalWorkflowActiveDomain convertEntityToDomain(InternalWorkflowEntity internalWorkflowEntity) {
        List<InternalWorkflowStageActiveDomain> internalWorkflowStageActiveDomains = new LinkedList<>();

        int index = 0;
        for (InternalWorkflowStageEntity stageEntity : internalWorkflowEntity.getStages()) {
            InternalWorkflowStageActiveDomain internalWorkflowStageActiveDomain = new InternalWorkflowStageActiveDomain();
            internalWorkflowStageActiveDomain.setIndex(index++);
            InternalWorkflowStageEntity.RepositoryRef repository = stageEntity.getRepository();
            if (repository != null)
                internalWorkflowStageActiveDomain.setRepository(new RepositoryDomain(repository.getOwner(), repository.getName()));
            internalWorkflowStageActiveDomain.setTask(stageEntity.getTask() != null ? InternalWorkflowStageActiveDomain.StageTask.valueOf(stageEntity.getTask()) : null);
            internalWorkflowStageActiveDomain.setCommitOperation(stageEntity.getCommitOperation() != null ? InternalWorkflowStageActiveDomain.CommitOperation.valueOf(stageEntity.getCommitOperation()) : null);
            internalWorkflowStageActiveDomain.setTargetBranch(stageEntity.getTargetBranch());
            internalWorkflowStageActiveDomain.setCommitFilePath(stageEntity.getCommitFilePath());
            internalWorkflowStageActiveDomain.setCommitFindRegex(stageEntity.getCommitFindRegex());
            internalWorkflowStageActiveDomain.setCommitContent(stageEntity.getCommitContent());
            internalWorkflowStageActiveDomain.setCommitMessage(stageEntity.getCommitMessage());
            internalWorkflowStageActiveDomain.setVersionPart(stageEntity.getVersionPart() != null ? InternalWorkflowStageActiveDomain.VersionPart.valueOf(stageEntity.getVersionPart()) : null);
            internalWorkflowStageActiveDomain.setWorkflowUrl(stageEntity.getWorkflowUrl());
            internalWorkflowStageActiveDomain.setRunMode(stageEntity.getRunMode() != null ? InternalWorkflowStageActiveDomain.RunMode.valueOf(stageEntity.getRunMode()) : null);
            internalWorkflowStageActiveDomains.add(internalWorkflowStageActiveDomain);
        }

        return new InternalWorkflowActiveDomain(internalWorkflowEntity.getName(), internalWorkflowStageActiveDomains);
    }

    /**
     * Converts a workflow DTO to a persistable entity.
     *
     * @param internalWorkflowDTO the DTO to convert
     * @return the corresponding entity
     */
    private InternalWorkflowEntity convertDtoToEntity(InternalWorkflowDTO internalWorkflowDTO) {
        List<InternalWorkflowStageEntity> stagesEntity = new LinkedList<>();

        for (InternalWorkflowStageDTO stageDTO : internalWorkflowDTO.getStages()) {
            InternalWorkflowStageEntity internalWorkflowStageEntity = new InternalWorkflowStageEntity();
            RepositoryDTO repositoryDTO = stageDTO.getRepository();
            if (repositoryDTO != null)
                internalWorkflowStageEntity.setRepository(new InternalWorkflowStageEntity.RepositoryRef(repositoryDTO.getOwner(), repositoryDTO.getName()));
            internalWorkflowStageEntity.setTask(stageDTO.getTask() != null ? stageDTO.getTask().name() : null);
            internalWorkflowStageEntity.setCommitOperation(stageDTO.getCommitOperation() != null ? stageDTO.getCommitOperation().name() : null);
            internalWorkflowStageEntity.setTargetBranch(stageDTO.getTargetBranch());
            internalWorkflowStageEntity.setCommitFilePath(stageDTO.getCommitFilePath());
            internalWorkflowStageEntity.setCommitFindRegex(stageDTO.getCommitFindRegex());
            internalWorkflowStageEntity.setCommitContent(stageDTO.getCommitContent());
            internalWorkflowStageEntity.setCommitMessage(stageDTO.getCommitMessage());
            internalWorkflowStageEntity.setVersionPart(stageDTO.getVersionPart() != null ? stageDTO.getVersionPart().name() : null);
            internalWorkflowStageEntity.setWorkflowUrl(stageDTO.getWorkflowUrl());
            internalWorkflowStageEntity.setRunMode(stageDTO.getRunMode() != null ? stageDTO.getRunMode().name() : null);
            stagesEntity.add(internalWorkflowStageEntity);
        }

        return new InternalWorkflowEntity(internalWorkflowDTO.getName(), stagesEntity);
    }

    /**
     * Converts a persisted workflow entity to its DTO representation.
     *
     * @param internalWorkflowEntity the entity to convert
     * @return the corresponding DTO
     */
    private InternalWorkflowDTO convertEntityToDto(InternalWorkflowEntity internalWorkflowEntity) {
        List<InternalWorkflowStageDTO> stages = new LinkedList<>();

        for (InternalWorkflowStageEntity stageEntity : internalWorkflowEntity.getStages()) {
            InternalWorkflowStageDTO stageDTO = new InternalWorkflowStageDTO();
            InternalWorkflowStageEntity.RepositoryRef repository = stageEntity.getRepository();
            if (repository != null)
                stageDTO.setRepository(new RepositoryDTO(repository.getOwner(), repository.getName()));
            stageDTO.setTask(stageEntity.getTask() != null ? InternalWorkflowStageDTO.StageTask.valueOf(stageEntity.getTask()) : null);
            stageDTO.setCommitOperation(stageEntity.getCommitOperation() != null ? InternalWorkflowStageDTO.CommitOperation.valueOf(stageEntity.getCommitOperation()) : null);
            stageDTO.setTargetBranch(stageEntity.getTargetBranch());
            stageDTO.setCommitFilePath(stageEntity.getCommitFilePath());
            stageDTO.setCommitFindRegex(stageEntity.getCommitFindRegex());
            stageDTO.setCommitContent(stageEntity.getCommitContent());
            stageDTO.setCommitMessage(stageEntity.getCommitMessage());
            stageDTO.setVersionPart(stageEntity.getVersionPart() != null ? InternalWorkflowStageDTO.VersionPart.valueOf(stageEntity.getVersionPart()) : null);
            stageDTO.setWorkflowUrl(stageEntity.getWorkflowUrl());
            stageDTO.setRunMode(stageEntity.getRunMode() != null ? InternalWorkflowStageDTO.RunMode.valueOf(stageEntity.getRunMode()) : null);
            stages.add(stageDTO);
        }

        return new InternalWorkflowDTO(internalWorkflowEntity.getName(), stages);
    }

    /**
     * Converts a list of active-stage domain objects to their DTO representations.
     *
     * @param internalWorkflowStageActiveDomains the domain objects to convert
     * @return the corresponding DTOs
     */
    private List<InternalWorkflowStageActiveDTO> convertStageDomainToDto(List<InternalWorkflowStageActiveDomain> internalWorkflowStageActiveDomains) {
        List<InternalWorkflowStageActiveDTO> internalWorkflowStageActiveDTOS = new LinkedList<>();

        for (InternalWorkflowStageActiveDomain internalWorkflowStageActiveDomain : internalWorkflowStageActiveDomains) {
            InternalWorkflowStageActiveDTO internalWorkflowStageActiveDTO = new InternalWorkflowStageActiveDTO();
            internalWorkflowStageActiveDTO.setIndex(internalWorkflowStageActiveDomain.getIndex());
            internalWorkflowStageActiveDTO.setTitle(buildStageTitle(internalWorkflowStageActiveDomain));
            internalWorkflowStageActiveDTO.setDetails(buildStageDetails(internalWorkflowStageActiveDomain));
            internalWorkflowStageActiveDTO.setStatus(internalWorkflowStageActiveDomain.getStatus().name().toLowerCase(Locale.ROOT));
            internalWorkflowStageActiveDTO.setParallel(internalWorkflowStageActiveDomain.getIndex() > 0
                    && internalWorkflowStageActiveDomain.getRunMode() == InternalWorkflowStageActiveDomain.RunMode.WITH_PREVIOUS);
            internalWorkflowStageActiveDTO.setRunUrl(internalWorkflowStageActiveDomain.getRunUrl());
            internalWorkflowStageActiveDTO.setError(internalWorkflowStageActiveDomain.getError());
            internalWorkflowStageActiveDTO.setStartedAt(internalWorkflowStageActiveDomain.getStartedAt());
            internalWorkflowStageActiveDTO.setExpectedDurationSeconds(internalWorkflowStageActiveDomain.getExpectedDurationSeconds());
            internalWorkflowStageActiveDTOS.add(internalWorkflowStageActiveDTO);
        }

        return internalWorkflowStageActiveDTOS;
    }

    /**
     * Builds the human-readable title of an active stage, combining its repository
     * and task type.
     *
     * @param stage the active stage to describe
     * @return the stage title, e.g. {@code owner/name - Commit}
     */
    private String buildStageTitle(InternalWorkflowStageActiveDomain stage) {
        StringBuilder title = new StringBuilder();

        RepositoryDomain repository = stage.getRepository();
        if (repository != null)
            title.append(repository.getOwner()).append("/").append(repository.getName());

        if (stage.getTask() != null) {
            if (!title.isEmpty())
                title.append(" - ");
            title.append(stage.getTask() == InternalWorkflowStageActiveDomain.StageTask.COMMIT ? "Commit" : "Pipeline");
        }

        return title.toString();
    }

    /**
     * Builds the detail text describing an active stage's action, mirroring the
     * summary shown in the workflow UI.
     *
     * @param stage the active stage to describe
     * @return the stage detail text
     */
    private String buildStageDetails(InternalWorkflowStageActiveDomain stage) {
        StringBuilder details = new StringBuilder();

        if (stage.getTask() == InternalWorkflowStageActiveDomain.StageTask.COMMIT) {
            InternalWorkflowStageActiveDomain.CommitOperation commitOperation = stage.getCommitOperation();
            if (commitOperation == InternalWorkflowStageActiveDomain.CommitOperation.BUMP_MAVEN_VERSION)
                details.append("bump Maven version");
            else if (commitOperation == InternalWorkflowStageActiveDomain.CommitOperation.FIND_AND_REPLACE_REGEX)
                details.append("replace /").append(stage.getCommitFindRegex()).append("/ in ").append(stage.getCommitFilePath());
            else if (commitOperation == InternalWorkflowStageActiveDomain.CommitOperation.SET_FILE_CONTENT)
                details.append("set content of ").append(stage.getCommitFilePath());
        } else if (stage.getTask() == InternalWorkflowStageActiveDomain.StageTask.WORKFLOW) {
            details.append("pipeline: ").append(stage.getWorkflowUrl());
        }

        if (stage.getTargetBranch() != null && !stage.getTargetBranch().isEmpty()) {
            if (!details.isEmpty())
                details.append(" ");
            details.append("on ").append(stage.getTargetBranch());
        }

        return details.toString();
    }

    /**
     * The ref a pipeline stage is dispatched on.
     *
     * @param stage the pipeline stage
     * @return its target branch, or {@value #DEFAULT_BRANCH} when it has none
     */
    private static String refOf(InternalWorkflowStageActiveDomain stage) {
        return stage.getTargetBranch() == null || stage.getTargetBranch().trim().isEmpty()
                ? DEFAULT_BRANCH
                : stage.getTargetBranch();
    }

    /**
     * Extracts the workflow file name (e.g. {@code ci.yml}) from a stage's
     * workflow URL or path, which is the identifier the dispatch endpoint expects.
     *
     * @param workflowUrl the workflow URL or path stored on the stage
     * @return the workflow file name
     * @throws IllegalStateException if the URL is missing or has no file segment
     */
    private static String extractWorkflowFileName(String workflowUrl) {
        if (workflowUrl == null || workflowUrl.trim().isEmpty())
            throw new IllegalStateException("Pipeline stage has no workflow URL");

        String fileName = workflowUrl.substring(workflowUrl.lastIndexOf('/') + 1);
        if (fileName.isEmpty())
            throw new IllegalStateException("Cannot extract workflow file name from: " + workflowUrl);
        return fileName;
    }

    /**
     * Bumps the project version in a {@code pom.xml} by the requested component,
     * resetting the lower components (a minor bump zeroes the patch, a major bump
     * zeroes both) and preserving any qualifier suffix (e.g. {@code -SNAPSHOT}).
     * Versions inside the {@code <parent>} block are skipped so the parent
     * reference is left untouched.
     *
     * @param pomContent  the current content of the pom.xml
     * @param versionPart which component to increment; {@code null} means patch
     * @return the content with the version bumped
     * @throws IllegalStateException if no version tag can be found
     */
    private static String bumpMavenVersion(String pomContent, InternalWorkflowStageActiveDomain.VersionPart versionPart) {
        InternalWorkflowStageActiveDomain.VersionPart part =
                versionPart == null ? InternalWorkflowStageActiveDomain.VersionPart.PATCH : versionPart;

        int parentStart = pomContent.indexOf("<parent>");
        int parentEnd = pomContent.indexOf("</parent>");

        Matcher matcher = MAVEN_VERSION_PATTERN.matcher(pomContent);
        while (matcher.find()) {
            if (parentStart >= 0 && matcher.start() > parentStart && matcher.start() < parentEnd)
                continue;

            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));
            switch (part) {
                case MAJOR -> {
                    major++;
                    minor = 0;
                    patch = 0;
                }
                case MINOR -> {
                    minor++;
                    patch = 0;
                }
                case PATCH -> patch++;
            }

            String bumped = "<version>" + major + "." + minor + "." + patch + matcher.group(4) + "</version>";
            return new StringBuilder(pomContent).replace(matcher.start(), matcher.end(), bumped).toString();
        }
        throw new IllegalStateException("No Maven version found to bump");
    }

    /**
     * Formats a run instant for the dashboard.
     *
     * @param instant the instant, if any
     * @return the text, or {@code null} without an instant
     */
    private static String format(Instant instant) {
        return instant == null ? null : TIME_FORMATTER.format(instant);
    }

    /**
     * How often GitHub is asked about dispatched pipelines.
     *
     * @param firstCheck       wait after the dispatches before the first check
     *                         (GitHub needs a moment to list the run)
     * @param interval         wait between checks
     * @param slowInterval     wait between checks when API calls run low
     * @param lowRateLimit     fewer API calls left than this means "low"
     * @param runLookupTimeout how long a dispatched run may take to show up
     *                         before the stage fails
     */
    record Pacing(Duration firstCheck, Duration interval, Duration slowInterval, int lowRateLimit, Duration runLookupTimeout) {
    }

    /**
     * Waits for a while; {@link Thread#sleep(Duration)} outside tests.
     */
    @FunctionalInterface
    interface Sleeper {

        /**
         * Waits.
         *
         * @param duration how long
         * @throws InterruptedException if the workflow was stopped meanwhile
         */
        void sleep(Duration duration) throws InterruptedException;

    }

    /**
     * Runs one workflow on a background thread: its stages group by group, a
     * group's pipelines followed on GitHub until they all succeed. A failing
     * stage fails the run and the stages after it don't start; stopping
     * interrupts the wait.
     */
    private final class Worker {

        /**
         * The user who started the workflow, whose GitHub token it runs with.
         */
        private final String userId;

        /**
         * The runtime state of the workflow being executed.
         */
        private final InternalWorkflowActiveDomain domain;

        /**
         * The thread running the workflow, while it runs.
         */
        private volatile Thread thread;

        /**
         * Whether the user asked to stop.
         */
        private volatile boolean stopRequested;

        private Worker(String userId, InternalWorkflowActiveDomain domain) {
            this.userId = userId;
            this.domain = domain;
        }

        /**
         * Runs the workflow to its end: succeeded, failed or stopped.
         */
        private void run() {
            thread = Thread.currentThread();
            domain.start(clock.instant());
            String name = domain.getWorkflowName();
            try {
                while (true) {
                    checkStopped();
                    List<InternalWorkflowStageActiveDomain> group = new ArrayList<>();
                    for (InternalWorkflowStageActiveDomain stage = domain.nextStage(); stage != null; stage = domain.nextStage())
                        group.add(stage);
                    if (group.isEmpty())
                        break;

                    runGroup(group);
                    domain.clearCurrentStages();
                }
                domain.finish(WorkflowStatus.SUCCEEDED, null, clock.instant());
                log.info("Workflow '{}' succeeded", name);
            } catch (InterruptedException e) {
                endUnfinishedStages();
                domain.finish(WorkflowStatus.STOPPED, "Stopped by the user", clock.instant());
                log.info("Workflow '{}' stopped", name);
            } catch (RuntimeException e) {
                endUnfinishedStages();
                domain.finish(WorkflowStatus.FAILED, e.getMessage(), clock.instant());
                log.warn("Workflow '{}' failed: {}", name, e.getMessage());
            } finally {
                thread = null;
            }
        }

        /**
         * Stops the workflow: interrupts its wait, or makes it stop before
         * its next step.
         */
        private void stop() {
            stopRequested = true;
            Thread running = thread;
            if (running != null)
                running.interrupt();
        }

        /**
         * Runs a group of stages: starts them all, then waits until its
         * pipelines' runs have succeeded.
         *
         * @param group the stages, in order
         * @throws InterruptedException if the workflow is stopped
         * @throws IllegalStateException if a stage fails
         */
        private void runGroup(List<InternalWorkflowStageActiveDomain> group) throws InterruptedException {
            String token = authService.getGithubToken(userId);
            for (InternalWorkflowStageActiveDomain stage : group) {
                checkStopped();
                startStage(stage, token);
            }
            waitForPipelines(group, token);
        }

        /**
         * Starts a stage: a commit is made and ends it; a pipeline is
         * dispatched and stays running until its run completes.
         *
         * @param stage the stage
         * @param token the GitHub token to authenticate with
         * @throws IllegalStateException if the stage can't be executed (the
         *                               stage is marked failed)
         */
        private void startStage(InternalWorkflowStageActiveDomain stage, String token) {
            stage.setStartedAt(clock.instant());
            stage.setStatus(StageStatus.RUNNING);
            try {
                if (stage.getTask() == InternalWorkflowStageActiveDomain.StageTask.COMMIT) {
                    executeCommitStage(stage, token);
                    stage.finish(StageStatus.SUCCEEDED, null, clock.instant());
                } else if (stage.getTask() == InternalWorkflowStageActiveDomain.StageTask.WORKFLOW) {
                    executeWorkflowStage(stage, token);
                } else {
                    throw new IllegalStateException("Stage " + stage.getIndex() + " has no task");
                }
            } catch (RuntimeException e) {
                stage.finish(StageStatus.FAILED, e.getMessage(), clock.instant());
                throw e;
            }
        }

        /**
         * Follows the group's dispatched pipelines on GitHub until every run
         * has succeeded, checking at the pace set by {@link Pacing}.
         *
         * @param group the group's stages
         * @param token the GitHub token to authenticate with
         * @throws InterruptedException  if the workflow is stopped
         * @throws IllegalStateException if a run doesn't succeed (its stage is
         *                               marked failed)
         */
        private void waitForPipelines(List<InternalWorkflowStageActiveDomain> group, String token) throws InterruptedException {
            List<InternalWorkflowStageActiveDomain> running = new ArrayList<>(group.stream()
                    .filter(stage -> stage.getStatus() == StageStatus.RUNNING)
                    .toList());
            if (running.isEmpty())
                return;

            sleeper.sleep(pacing.firstCheck());
            while (true) {
                checkStopped();
                Iterator<InternalWorkflowStageActiveDomain> stages = running.iterator();
                while (stages.hasNext()) {
                    InternalWorkflowStageActiveDomain stage = stages.next();
                    boolean succeeded;
                    try {
                        succeeded = checkPipeline(stage, token);
                    } catch (RuntimeException e) {
                        stage.finish(StageStatus.FAILED, e.getMessage(), clock.instant());
                        throw e;
                    }
                    if (succeeded) {
                        stage.finish(StageStatus.SUCCEEDED, null, clock.instant());
                        stages.remove();
                    }
                }
                if (running.isEmpty())
                    return;
                sleeper.sleep(nextCheckIn(token));
            }
        }

        /**
         * Throws when the user asked to stop, so the run ends before its next
         * step.
         *
         * @throws InterruptedException if a stop was asked
         */
        private void checkStopped() throws InterruptedException {
            if (stopRequested)
                throw new InterruptedException("Stopped");
        }

        /**
         * Marks the stages still running as stopped: the run no longer
         * follows them (GitHub may still finish their runs).
         */
        private void endUnfinishedStages() {
            for (InternalWorkflowStageActiveDomain stage : domain.getStages()) {
                if (stage.getStatus() == StageStatus.RUNNING)
                    stage.finish(StageStatus.STOPPED, null, clock.instant());
            }
        }

    }

}
