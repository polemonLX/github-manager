package com.polemon.github_manager.service;

import com.polemon.api_lib.persistence.exception.PersistenceException;
import com.polemon.github_manager.client.GitHubClient;
import com.polemon.github_manager.exceptions.InvalidWorkflowException;
import com.polemon.github_manager.model.domain.InternalWorkflowActiveDomain;
import com.polemon.github_manager.model.domain.InternalWorkflowStageActiveDomain;
import com.polemon.github_manager.model.domain.RepositoryDomain;
import com.polemon.github_manager.model.dto.*;
import com.polemon.github_manager.model.dto.api.FileContentAPI;
import com.polemon.github_manager.model.entity.InternalWorkflowEntity;
import com.polemon.github_manager.model.entity.InternalWorkflowStageEntity;
import com.polemon.github_manager.repository.WorkflowRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service class for managing each user's internal workflows.
 * This service provides methods to retrieve and persist internal workflows,
 * delegating storage to the {@link WorkflowRepository}, and runs them with the
 * GitHub token of the user who started them.
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
     * Store for persisting and retrieving internal workflows.
     */
    private final WorkflowRepository workflowRepository;

    /**
     * Client for the GitHub API calls performed by workflow stages
     * (commits and pipeline dispatches).
     */
    private final GitHubClient gitHubClient;

    /**
     * Service used to retrieve each user's GitHub token for API calls.
     */
    private final AuthService authService;

    /**
     * Active workflows per user id, each keyed by workflow name.
     */
    private final Map<String, Map<String, InternalWorkflowActiveWorker>> internalWorkflowActiveDomainMap;

    public WorkflowService(WorkflowRepository workflowRepository, GitHubClient gitHubClient, AuthService authService) {
        this.workflowRepository = workflowRepository;
        this.gitHubClient = gitHubClient;
        this.authService = authService;

        this.internalWorkflowActiveDomainMap = new ConcurrentHashMap<>();
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
     * Retrieves a user's currently active workflows.
     *
     * @param userId the user id
     * @return the list of active workflows
     */
    public List<InternalWorkflowActiveDTO> getInternalWorkflowActiveList(String userId) {
        Map<String, InternalWorkflowActiveWorker> active = activeWorkflowsOf(userId);
        synchronized (active) {
            return active.values().stream()
                    .map(worker -> convertDomainToDto(worker.getInternalWorkflowActiveDomain()))
                    .toList();
        }
    }

    /**
     * Starts a workflow from the submitted definition, including the per-stage
     * runtime values entered in the start dialog. The workflow must exist among
     * the user's stored workflows; the submitted stages (with any start-time
     * overrides) drive this run without changing what is persisted.
     *
     * @param userId   the user id
     * @param workflow the workflow name and its stages, as submitted by the start dialog
     * @throws InvalidWorkflowException if the workflow is empty or the user has no workflow with the given name
     */
    public void startWorkflow(String userId, InternalWorkflowDTO workflow) throws InvalidWorkflowException {
        if (workflow == null || workflow.getName() == null || workflow.getName().trim().isEmpty())
            throw new InvalidWorkflowException("Workflow name must not be empty");

        String workflowName = workflow.getName();

        if (findByName(userId, workflowName).isEmpty())
            throw new InvalidWorkflowException("Internal workflow not found: " + workflowName);

        InternalWorkflowActiveDomain internalWorkflowActiveDomain = convertEntityToDomain(convertDtoToEntity(workflow));

        InternalWorkflowActiveWorker internalWorkflowActiveWorker = new InternalWorkflowActiveWorker(userId, workflowName, internalWorkflowActiveDomain);
        activeWorkflowsOf(userId).put(workflowName, internalWorkflowActiveWorker);
        internalWorkflowActiveWorker.start();
    }

    /**
     * Stops the user's workflow with the given name, if it is currently active.
     *
     * @param userId       the user id
     * @param workflowName the name of the workflow to stop
     */
    public void stopWorkflow(String userId, String workflowName) {
        InternalWorkflowActiveWorker worker = activeWorkflowsOf(userId).remove(workflowName);
        if (worker != null)
            worker.stop();
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
     * The active workflows of a user, created empty on first use.
     *
     * @param userId the user id
     * @return the user's active workflows, keyed by workflow name, in start order
     */
    private Map<String, InternalWorkflowActiveWorker> activeWorkflowsOf(String userId) {
        return internalWorkflowActiveDomainMap.computeIfAbsent(userId, id -> Collections.synchronizedMap(new LinkedHashMap<>()));
    }

    /**
     * Converts an {@link InternalWorkflowActiveDomain} to its DTO representation.
     *
     * @param workflowActiveDomain the domain object to convert
     * @return the corresponding DTO
     */
    private InternalWorkflowActiveDTO convertDomainToDto(InternalWorkflowActiveDomain workflowActiveDomain) {
        InternalWorkflowActiveDTO internalWorkflowActiveDTO = new InternalWorkflowActiveDTO();
        internalWorkflowActiveDTO.setWorkflowName(workflowActiveDomain.getWorkflowName());
        internalWorkflowActiveDTO.setStages(convertStageDomainToDto(workflowActiveDomain.getStages()));
        internalWorkflowActiveDTO.setCurrentStages(convertStageDomainToDto(workflowActiveDomain.getCurrentStages()));
        internalWorkflowActiveDTO.setNextStages(convertStageDomainToDto(workflowActiveDomain.getNextStages()));
        internalWorkflowActiveDTO.setStartedAt(workflowActiveDomain.getStartedAt());
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
            internalWorkflowStageActiveDTO.setStatus(internalWorkflowStageActiveDomain.getStartedAt() == null ? "pending" : "running");
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
     * Worker that drives the execution of a single active internal workflow on a background thread.
     */
    private class InternalWorkflowActiveWorker {

        /**
         * The user who started the workflow, whose GitHub token it runs with.
         */
        private final String userId;

        /**
         * The name of the workflow being executed.
         */
        private final String workflowName;

        /**
         * The runtime state of the workflow being executed.
         */
        @Getter
        private final InternalWorkflowActiveDomain internalWorkflowActiveDomain;

        /**
         * The background thread running this worker.
         */
        private final Thread workerThread;

        public InternalWorkflowActiveWorker(String userId, String workflowName, InternalWorkflowActiveDomain internalWorkflowActiveDomain) {
            this.userId = userId;
            this.workflowName = workflowName;
            this.internalWorkflowActiveDomain = internalWorkflowActiveDomain;
            this.workerThread = new Thread(this::runWorkflow);
        }

        /**
         * Starts the worker thread to begin executing the workflow.
         */
        public void start() {
            workerThread.start();
        }

        /**
         * Interrupts the worker thread, stopping workflow execution.
         */
        public void stop() {
            workerThread.interrupt();
        }

        /**
         * Executes the workflow stages in sequence on the worker thread.
         * Stages are consumed from the domain's stage queue until none remain.
         */
        private void runWorkflow() {
            try {
                while (true) {
                    List<InternalWorkflowStageActiveDomain> nextActiveStages = new LinkedList<>();
                    while (true) {
                        InternalWorkflowStageActiveDomain internalWorkflowStageActive = internalWorkflowActiveDomain.nextStage();
                        if (internalWorkflowStageActive == null)
                            break;
                        nextActiveStages.add(internalWorkflowStageActive);
                    }

                    if (nextActiveStages.isEmpty())
                        break;

                    executeStages(nextActiveStages);
                    waitForStagesCompletion(nextActiveStages);
                    internalWorkflowActiveDomain.clearCurrentStages();
                }
            } catch (Exception e) {
                log.error("Error while executing workflow: {}", workflowName, e);
            }
        }

        /**
         * Executes the given stages: commit stages are committed through the GitHub
         * contents API and pipeline stages are dispatched as {@code workflow_dispatch}
         * events. Each stage's start instant is recorded as it begins. A stage that
         * cannot be executed aborts the workflow by propagating an exception to
         * {@link #runWorkflow()}.
         *
         * @param nextActiveStages the stages to execute
         */
        private void executeStages(List<InternalWorkflowStageActiveDomain> nextActiveStages) {
            String token = authService.getGithubToken(userId);

            for (InternalWorkflowStageActiveDomain stage : nextActiveStages) {
                stage.setStartedAt(Instant.now());
                if (stage.getTask() == InternalWorkflowStageActiveDomain.StageTask.COMMIT) {
                    executeCommitStage(stage, token);
                } else if (stage.getTask() == InternalWorkflowStageActiveDomain.StageTask.WORKFLOW) {
                    executeWorkflowStage(stage, token);
                }
            }
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
                case SET_FILE_CONTENT -> newContent = stage.getCommitContent();
                default ->
                        throw new IllegalStateException("Unsupported commit operation: " + stage.getCommitOperation());
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

            String owner = repository.getOwner();
            String name = repository.getName();
            String workflowFileName = extractWorkflowFileName(stage.getWorkflowUrl());
            String ref = stage.getTargetBranch() == null || stage.getTargetBranch().trim().isEmpty()
                    ? DEFAULT_BRANCH
                    : stage.getTargetBranch();

            if (!gitHubClient.dispatchWorkflow(owner, name, workflowFileName, ref, token))
                throw new IllegalStateException("Failed to dispatch pipeline " + workflowFileName + " on " + owner + "/" + name);
        }

        /**
         * Extracts the workflow file name (e.g. {@code ci.yml}) from a stage's
         * workflow URL or path, which is the identifier the dispatch endpoint expects.
         *
         * @param workflowUrl the workflow URL or path stored on the stage
         * @return the workflow file name
         * @throws IllegalStateException if the URL is missing or has no file segment
         */
        private String extractWorkflowFileName(String workflowUrl) {
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
        private String bumpMavenVersion(String pomContent, InternalWorkflowStageActiveDomain.VersionPart versionPart) {
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
         * Waits for the given stages to complete (placeholder; not yet implemented).
         *
         * @param stages the stages to wait for
         */
        private void waitForStagesCompletion(List<InternalWorkflowStageActiveDomain> stages) throws InterruptedException {
            boolean isFinished = false;
            while (!isFinished) {
                Thread.sleep(3000);
                for (InternalWorkflowStageActiveDomain stage : stages) {
                    if (stage.getTask() == InternalWorkflowStageActiveDomain.StageTask.WORKFLOW) {

                    }
                }
            }
        }

    }

}
