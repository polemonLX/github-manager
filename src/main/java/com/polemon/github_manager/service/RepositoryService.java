package com.polemon.github_manager.service;

import com.polemon.api_lib.persistence.exception.PersistenceException;
import com.polemon.github_manager.client.GitHubClient;
import com.polemon.github_manager.exceptions.InvalidRepositoryException;
import com.polemon.github_manager.model.domain.*;
import com.polemon.github_manager.model.dto.*;
import com.polemon.github_manager.model.dto.api.*;
import com.polemon.github_manager.model.entity.RepositoryEntity;
import com.polemon.github_manager.model.entity.RepositoryGroup;
import com.polemon.github_manager.model.entity.RepositoryGroupMember;
import com.polemon.github_manager.model.entity.TrackedRepository;
import com.polemon.github_manager.repository.RepositoryGroupMemberRepository;
import com.polemon.github_manager.repository.RepositoryGroupRepository;
import com.polemon.github_manager.repository.RepositoryRepository;
import com.polemon.github_manager.repository.TrackedRepositoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service class for managing the GitHub repositories each user tracks.
 * This service provides methods to add, remove, and validate repositories.
 * It delegates GitHub API interactions to the GitHubClient.
 * <p>
 * A repository is stored once, with its GitHub data as of the last sync, and
 * linked to every user who tracks it. Pages read only the database; GitHub is
 * called when a repository is added and when it is synced (by the scheduler or
 * on request).
 */
@Slf4j
@Service
public class RepositoryService {

    /**
     * Format of the last sync instant shown on the pages.
     */
    private static final DateTimeFormatter LAST_SYNCED_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy").withZone(ZoneId.systemDefault());

    /**
     * Longest accepted group name.
     */
    static final int MAX_GROUP_NAME = 64;

    /**
     * Service used to retrieve each user's GitHub token for API calls.
     */
    private final AuthService authService;

    /**
     * Store of the repositories and their GitHub data.
     */
    private final RepositoryRepository repositoryRepository;

    /**
     * Store of which user tracks which repository.
     */
    private final TrackedRepositoryRepository trackedRepositoryRepository;

    /**
     * Store of the users' repository groups.
     */
    private final RepositoryGroupRepository groupRepository;

    /**
     * Store of which tracked repository is in which group.
     */
    private final RepositoryGroupMemberRepository groupMemberRepository;

    /**
     * Client for GitHub API interactions (validation and sync).
     */
    private final GitHubClient gitHubClient;

    public RepositoryService(RepositoryRepository repositoryRepository, TrackedRepositoryRepository trackedRepositoryRepository,
                             RepositoryGroupRepository groupRepository, RepositoryGroupMemberRepository groupMemberRepository,
                             GitHubClient gitHubClient, AuthService authService) {
        this.repositoryRepository = repositoryRepository;
        this.trackedRepositoryRepository = trackedRepositoryRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.gitHubClient = gitHubClient;
        this.authService = authService;
    }

    /**
     * Adds a repository to a user's tracked repositories. The user's token must
     * reach it on GitHub, even when other users already track it. A repository
     * nobody tracked yet is stored and synced right away.
     *
     * @param userId        the user id
     * @param repositoryDTO The repository to be added
     * @throws InvalidRepositoryException If the user has no token, or the repository is invalid or already tracked by the user
     */
    public void addRepository(String userId, RepositoryDTO repositoryDTO) throws InvalidRepositoryException {
        String owner = repositoryDTO.getOwner();
        String name = repositoryDTO.getName();

        Optional<RepositoryEntity> stored = repositoryRepository.findByOwnerAndName(owner, name);
        if (stored.isPresent() && isTrackedBy(userId, stored.get().getId()))
            throw new InvalidRepositoryException("Repository already exists: " + owner + "/" + name);

        String token = authService.getGithubToken(userId);
        if (token == null)
            throw new InvalidRepositoryException("Set a GitHub token in Settings before adding repositories");

        if (!gitHubClient.repositoryExists(owner, name, token))
            throw new InvalidRepositoryException("Repository not found or no permission to access: " + owner + "/" + name);

        RepositoryEntity repository = stored.isPresent() ? stored.get() : createRepository(owner, name, token);
        try {
            trackedRepositoryRepository.create(new TrackedRepository(UUID.randomUUID().toString(), userId, repository.getId()));
        } catch (PersistenceException e) {
            if (e.getReason() != PersistenceException.Reason.DUPLICATE)
                throw e;
            throw new InvalidRepositoryException("Repository already exists: " + owner + "/" + name);
        }
    }

    /**
     * Retrieves a user's tracked repositories with their data as of the last
     * sync, read from the database (no GitHub API calls on the request path).
     *
     * @param userId the user id
     * @return the repositories, sorted by owner and name
     */
    public List<RepositoryDTO> getRepositories(String userId) {
        return trackedRepositories(userId).stream()
                .sorted(Comparator.comparing(RepositoryEntity::getOwner).thenComparing(RepositoryEntity::getName))
                .map(this::convertEntityToDTO)
                .toList();
    }

    /**
     * Retrieves a user's tracked repositories with the groups each is in,
     * optionally only those of one group. Like {@link #getRepositories(String)},
     * from the database only.
     *
     * @param userId  the user id
     * @param groupId the group to show, or {@code null} for all; a group that
     *                isn't the user's also shows all
     * @return the repositories, sorted by owner and name, each with its
     *         {@code groupIds}
     */
    public List<RepositoryDTO> getRepositories(String userId, String groupId) {
        Map<String, List<String>> groupsByLink = new HashMap<>();
        Set<String> groupIds = new HashSet<>();
        for (RepositoryGroup group : groupRepository.findByUser(userId)) {
            groupIds.add(group.getId());
            for (RepositoryGroupMember member : groupMemberRepository.findByGroup(group.getId()))
                groupsByLink.computeIfAbsent(member.getTrackedRepositoryId(), link -> new ArrayList<>()).add(group.getId());
        }
        boolean filtered = groupId != null && groupIds.contains(groupId);

        List<RepositoryDTO> repositories = new ArrayList<>();
        for (TrackedRepository link : trackedRepositoryRepository.findByUser(userId)) {
            List<String> linkGroups = groupsByLink.getOrDefault(link.getId(), List.of());
            if (filtered && !linkGroups.contains(groupId))
                continue;
            repositoryRepository.findById(link.getRepositoryId()).ifPresent(repository -> {
                RepositoryDTO repositoryDTO = convertEntityToDTO(repository);
                repositoryDTO.setGroupIds(new ArrayList<>(linkGroups));
                repositories.add(repositoryDTO);
            });
        }
        repositories.sort(Comparator.comparing(RepositoryDTO::getOwner).thenComparing(RepositoryDTO::getName));
        return repositories;
    }

    /**
     * Lists a user's repository groups.
     *
     * @param userId the user id
     * @return the groups, sorted by name (case-insensitive)
     */
    public List<RepositoryGroupDTO> getGroups(String userId) {
        return groupRepository.findByUser(userId).stream()
                .map(group -> new RepositoryGroupDTO(group.getId(), group.getName()))
                .sorted(Comparator.comparing(RepositoryGroupDTO::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Creates a repository group for a user.
     *
     * @param userId the user id
     * @param name   the group's name; surrounding spaces are dropped
     * @return the new group
     * @throws IllegalArgumentException if the name is empty, longer than
     *                                  {@value #MAX_GROUP_NAME} characters, or
     *                                  already used by one of the user's groups
     */
    public RepositoryGroupDTO createGroup(String userId, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_GROUP_NAME)
            throw new IllegalArgumentException("The group name must have 1 to " + MAX_GROUP_NAME + " characters");

        RepositoryGroup group = new RepositoryGroup(UUID.randomUUID().toString(), userId, trimmed);
        try {
            groupRepository.create(group);
        } catch (PersistenceException e) {
            if (e.getReason() != PersistenceException.Reason.DUPLICATE)
                throw e;
            throw new IllegalArgumentException("A group named '" + trimmed + "' already exists");
        }
        log.info("Created group '{}'", trimmed);
        return new RepositoryGroupDTO(group.getId(), group.getName());
    }

    /**
     * Deletes one of a user's groups. Its memberships go with it (the store
     * cascades them); the repositories stay tracked. A group that doesn't
     * exist or isn't the user's is left alone.
     *
     * @param userId  the user id
     * @param groupId the group to delete
     * @return {@code true} if a group was deleted
     */
    public boolean deleteGroup(String userId, String groupId) {
        Optional<RepositoryGroup> group = groupId == null ? Optional.empty() : groupRepository.findById(groupId);
        if (group.isEmpty() || !group.get().getUserId().equals(userId)) {
            log.debug("Group {} not deleted: not one of the user's groups", groupId);
            return false;
        }
        groupRepository.deleteById(groupId);
        log.info("Deleted group '{}'", group.get().getName());
        return true;
    }

    /**
     * Sets the groups one of a user's tracked repositories is in: it joins
     * the given groups and leaves the others.
     *
     * @param userId   the user id
     * @param owner    the repository owner
     * @param name     the repository name
     * @param groupIds the groups it must be in; {@code null} or empty for none
     * @throws IllegalArgumentException if the user doesn't track the
     *                                  repository, or a group isn't the user's
     */
    public void setGroups(String userId, String owner, String name, Collection<String> groupIds) {
        TrackedRepository link = trackedLink(userId, owner, name)
                .orElseThrow(() -> new IllegalArgumentException("Repository not tracked: " + owner + "/" + name));
        Set<String> wanted = groupIds == null ? Set.of() : new HashSet<>(groupIds);
        Set<String> owned = new HashSet<>();
        groupRepository.findByUser(userId).forEach(group -> owned.add(group.getId()));
        if (!owned.containsAll(wanted))
            throw new IllegalArgumentException("Unknown group");

        Set<String> current = new HashSet<>();
        for (RepositoryGroupMember member : groupMemberRepository.findByTrackedRepository(link.getId())) {
            if (wanted.contains(member.getGroupId()))
                current.add(member.getGroupId());
            else
                groupMemberRepository.deleteById(member.getId());
        }
        for (String groupId : wanted) {
            if (!current.contains(groupId))
                groupMemberRepository.create(new RepositoryGroupMember(UUID.randomUUID().toString(), groupId, link.getId()));
        }
        log.info("Repository {}/{} is now in {} group(s)", owner, name, wanted.size());
    }

    /**
     * Removes a repository from a user's tracked repositories. The repository
     * itself is deleted once nobody tracks it.
     *
     * @param userId        the user id
     * @param repositoryDTO The repository to be removed
     */
    public void removeRepository(String userId, RepositoryDTO repositoryDTO) {
        repositoryRepository.findByOwnerAndName(repositoryDTO.getOwner(), repositoryDTO.getName())
                .ifPresent(repository -> untrack(userId, repository.getId()));
    }

    /**
     * Synchronizes every stored repository, each with the token of one of the
     * users who track it. A repository whose users have no token is skipped, and
     * a failure on one repository is logged and doesn't stop the others.
     * Invoked by the scheduler.
     */
    public void syncAll() {
        for (RepositoryEntity repository : repositoryRepository.getAll()) {
            Optional<String> token = trackedRepositoryRepository.findByRepository(repository.getId()).stream()
                    .map(link -> authService.getGithubToken(link.getUserId()))
                    .filter(Objects::nonNull)
                    .findFirst();
            if (token.isEmpty()) {
                log.debug("Skipped {}/{}: none of its users has a token", repository.getOwner(), repository.getName());
                continue;
            }

            try {
                sync(repository, token.get());
            } catch (RuntimeException e) {
                log.warn("Could not sync {}/{}: {}", repository.getOwner(), repository.getName(), e.getMessage());
            }
        }
    }

    /**
     * Synchronizes all of a user's tracked repositories with the user's token.
     *
     * @param userId the user id
     */
    public void sync(String userId) {
        String token = authService.getGithubToken(userId);
        for (RepositoryEntity repository : trackedRepositories(userId)) {
            sync(repository, token);
        }
    }

    /**
     * Synchronizes one of a user's tracked repositories with the user's token.
     * Nothing happens if the user doesn't track it.
     *
     * @param userId the user id
     * @param owner  The owner of the repository
     * @param name   The name of the repository
     */
    public void sync(String userId, String owner, String name) {
        trackedRepository(userId, owner, name).ifPresent(repository -> sync(repository, authService.getGithubToken(userId)));
    }

    /**
     * Synchronizes the pull requests of all of a user's tracked repositories.
     *
     * @param userId the user id
     */
    public void syncPullRequest(String userId) {
        String token = authService.getGithubToken(userId);
        for (RepositoryEntity repository : trackedRepositories(userId)) {
            syncPullRequests(repository, token);
        }
    }

    /**
     * Synchronizes the pull requests of one of a user's tracked repositories.
     * Nothing happens if the user doesn't track it.
     *
     * @param userId the user id
     * @param owner  The owner of the repository
     * @param name   The name of the repository
     */
    public void syncPullRequest(String userId, String owner, String name) {
        trackedRepository(userId, owner, name).ifPresent(repository -> syncPullRequests(repository, authService.getGithubToken(userId)));
    }

    /**
     * Removes all of a user's tracked repositories; the ones nobody else tracks
     * are deleted.
     *
     * @param userId the user id
     */
    public void clear(String userId) {
        for (TrackedRepository link : trackedRepositoryRepository.findByUser(userId)) {
            untrack(userId, link.getRepositoryId());
        }
    }

    /**
     * Stores a repository nobody tracked yet and syncs it. If another user
     * stored it meanwhile, that entry is used.
     *
     * @param owner the repository owner
     * @param name  the repository name
     * @param token the GitHub token to sync with
     * @return the stored repository
     */
    private RepositoryEntity createRepository(String owner, String name, String token) {
        RepositoryEntity repository = new RepositoryEntity(UUID.randomUUID().toString(), owner, name);
        try {
            repositoryRepository.create(repository);
        } catch (PersistenceException e) {
            if (e.getReason() != PersistenceException.Reason.DUPLICATE)
                throw e;
            return repositoryRepository.findByOwnerAndName(owner, name).orElseThrow(() -> e);
        }

        sync(repository, token);
        return repository;
    }

    /**
     * Removes a user's link to a repository, and the repository once nobody
     * tracks it.
     *
     * @param userId       the user id
     * @param repositoryId the repository id
     */
    private void untrack(String userId, String repositoryId) {
        boolean trackedByOthers = false;
        for (TrackedRepository link : trackedRepositoryRepository.findByRepository(repositoryId)) {
            if (link.getUserId().equals(userId))
                trackedRepositoryRepository.deleteById(link.getId());
            else
                trackedByOthers = true;
        }

        if (!trackedByOthers)
            repositoryRepository.deleteById(repositoryId);
    }

    /**
     * Tells whether a user tracks a repository.
     *
     * @param userId       the user id
     * @param repositoryId the repository id
     * @return {@code true} if the user tracks it
     */
    private boolean isTrackedBy(String userId, String repositoryId) {
        return trackedRepositoryRepository.findByUser(userId).stream()
                .anyMatch(link -> link.getRepositoryId().equals(repositoryId));
    }

    /**
     * Finds a user's link to one of the repositories they track.
     *
     * @param userId the user id
     * @param owner  the repository owner
     * @param name   the repository name
     * @return the link, or empty if the user doesn't track it
     */
    private Optional<TrackedRepository> trackedLink(String userId, String owner, String name) {
        return repositoryRepository.findByOwnerAndName(owner, name)
                .flatMap(repository -> trackedRepositoryRepository.findByUser(userId).stream()
                        .filter(link -> link.getRepositoryId().equals(repository.getId()))
                        .findFirst());
    }

    /**
     * Lists the repositories a user tracks.
     *
     * @param userId the user id
     * @return the user's repositories
     */
    private List<RepositoryEntity> trackedRepositories(String userId) {
        return trackedRepositoryRepository.findByUser(userId).stream()
                .map(link -> repositoryRepository.findById(link.getRepositoryId()))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Finds one of the repositories a user tracks.
     *
     * @param userId the user id
     * @param owner  the repository owner
     * @param name   the repository name
     * @return the repository, or empty if the user doesn't track it
     */
    private Optional<RepositoryEntity> trackedRepository(String userId, String owner, String name) {
        return repositoryRepository.findByOwnerAndName(owner, name)
                .filter(repository -> isTrackedBy(userId, repository.getId()));
    }

    /**
     * Synchronizes a repository: fetches its live data from GitHub and stores it.
     *
     * @param repository the repository to be synchronized
     * @param token      the GitHub token to fetch with
     */
    private void sync(RepositoryEntity repository, String token) {
        String owner = repository.getOwner();
        String name = repository.getName();

        List<TagAPI> tags = gitHubClient.getTags(owner, name, token);
        tags = tags.stream().peek(tag -> {
            String tagName = tag.getName();
            if (tagName.startsWith("v"))
                tag.setName(tagName.substring(1));
        }).sorted((t1, t2) -> t2.getName().compareTo(t1.getName())).toList();

        List<WorkflowRunAPI> runsInProgress = gitHubClient.getActionsRunning(owner, name, token);
        repository.setReleases(convertTagAPIToDomain(tags));
        repository.setActionsRunning(runsInProgress.size());
        repository.setPullRequests(convertPullRequestAPIToDomain(gitHubClient.getOpenPullRequests(owner, name, token)));
        repository.setBranches(convertBranchAPIToDomain(gitHubClient.getBranches(owner, name, token)));
        repository.setWorkflows(convertWorkflowAPIToDomain(gitHubClient.getWorkflows(owner, name, token), runsInProgress));
        repository.setLastSyncedAt(System.currentTimeMillis());

        repositoryRepository.update(repository);
    }

    /**
     * Synchronizes only the open pull requests of a repository and stores them.
     *
     * @param repository the repository
     * @param token      the GitHub token to fetch with
     */
    private void syncPullRequests(RepositoryEntity repository, String token) {
        repository.setPullRequests(convertPullRequestAPIToDomain(gitHubClient.getOpenPullRequests(repository.getOwner(), repository.getName(), token)));
        repositoryRepository.update(repository);
    }

    /**
     * Converts a stored repository to the DTO the pages render, splitting its
     * branches by type.
     *
     * @param repository the stored repository
     * @return the corresponding DTO
     */
    private RepositoryDTO convertEntityToDTO(RepositoryEntity repository) {
        RepositoryDTO repositoryDTO = new RepositoryDTO();
        repositoryDTO.setOwner(repository.getOwner());
        repositoryDTO.setName(repository.getName());
        repositoryDTO.setAllBranches(convertBranchDomainToDTO(repository.getBranches()));
        repositoryDTO.setFeatureBranches(repositoryDTO.getAllBranches().stream().filter(branch -> branch.getName().startsWith("feature/")).toList());
        repositoryDTO.setBugfixBranches(repositoryDTO.getAllBranches().stream().filter(branch -> branch.getName().startsWith("bugfix/")).toList());
        repositoryDTO.setOtherBranches(repositoryDTO.getAllBranches().stream().filter(branch -> !branch.getName().startsWith("feature/") && !branch.getName().startsWith("bugfix/") &&
                !branch.getName().startsWith("release/") && !branch.getName().equals("main")).toList());
        repositoryDTO.setOpenPullRequests(convertPullRequestDomainToDTO(repository.getPullRequests()));
        repositoryDTO.setActionsRunning(convertActionsRunningDomainToDTO(repository.getActionsRunning()));
        repositoryDTO.setReleases(convertReleaseDomainToDTO(repository.getReleases()));
        repositoryDTO.setWorkflows(convertWorkflowDomainToDTO(repository.getWorkflows()));
        repositoryDTO.setLastSyncedAt(repository.getLastSyncedAt() == 0 ? null : LAST_SYNCED_FORMATTER.format(Instant.ofEpochMilli(repository.getLastSyncedAt())));
        return repositoryDTO;
    }

    /**
     * Builds a list of placeholder {@link ActionsRunningDTO} objects from a run count.
     *
     * @param actionsRunning the number of active runs
     * @return a list of that many empty DTOs
     */
    private List<ActionsRunningDTO> convertActionsRunningDomainToDTO(int actionsRunning) {
        List<ActionsRunningDTO> actionsRunningDTOs = new LinkedList<>();
        for (int i = 0; i < actionsRunning; i++) {
            ActionsRunningDTO actionsRunningDTO = new ActionsRunningDTO();
            actionsRunningDTOs.add(actionsRunningDTO);
        }
        return actionsRunningDTOs;
    }

    /**
     * Converts a list of pull-request domain objects to their DTO representations.
     *
     * @param pullRequestDomains the domain objects to convert
     * @return the corresponding DTOs
     */
    private List<PullRequestDTO> convertPullRequestDomainToDTO(List<PullRequestDomain> pullRequestDomains) {
        List<PullRequestDTO> pullRequestDTOs = new LinkedList<>();
        for (PullRequestDomain pullRequestDomain : pullRequestDomains) {
            PullRequestDTO pullRequestDTO = new PullRequestDTO();
            pullRequestDTO.setNumber(pullRequestDomain.getNumber());
            pullRequestDTO.setTitle(pullRequestDomain.getTitle());
            pullRequestDTO.setAuthor(pullRequestDomain.getAuthor());
            pullRequestDTO.setCreatedAt(pullRequestDomain.getCreatedAt());
            pullRequestDTO.setUrl(pullRequestDomain.getUrl());
            pullRequestDTOs.add(pullRequestDTO);
        }
        return pullRequestDTOs;
    }

    /**
     * Converts a list of branch domain objects to their DTO representations.
     *
     * @param branchDomains the domain objects to convert
     * @return the corresponding DTOs
     */
    private List<BranchDTO> convertBranchDomainToDTO(List<BranchDomain> branchDomains) {
        List<BranchDTO> branchDTOs = new LinkedList<>();
        for (BranchDomain branchDomain : branchDomains) {
            BranchDTO branchDTO = new BranchDTO();
            branchDTO.setName(branchDomain.getName());
            branchDTO.setProtectedBranch(branchDomain.isProtectedBranch());
            branchDTOs.add(branchDTO);
        }
        return branchDTOs;
    }

    /**
     * Converts a list of tag domain objects to release DTO representations.
     *
     * @param releases the tag domain objects to convert
     * @return the corresponding release DTOs
     */
    private List<ReleaseDTO> convertReleaseDomainToDTO(List<TagDomain> releases) {
        List<ReleaseDTO> releaseDTOs = new LinkedList<>();
        for (TagDomain tagDomain : releases) {
            ReleaseDTO releaseDTO = new ReleaseDTO();
            releaseDTO.setName(tagDomain.getName());
            releaseDTOs.add(releaseDTO);
        }
        return releaseDTOs;
    }

    /**
     * Converts a list of workflow domain objects to their DTO representations.
     *
     * @param workflows the domain objects to convert
     * @return the corresponding DTOs
     */
    private List<WorkflowDTO> convertWorkflowDomainToDTO(List<WorkflowDomain> workflows) {
        List<WorkflowDTO> workflowDTOs = new LinkedList<>();
        for (WorkflowDomain workflowDomain : workflows) {
            WorkflowDTO workflowDTO = new WorkflowDTO();
            workflowDTO.setName(workflowDomain.getName());
            workflowDTO.setUrl(workflowDomain.getUrl());
            workflowDTO.setState(workflowDomain.getState());
            workflowDTO.setRunning(workflowDomain.isRunning());
            workflowDTOs.add(workflowDTO);
        }
        return workflowDTOs;
    }

    /**
     * Converts a list of pull-request API objects to domain objects.
     *
     * @param openPullRequests the API objects to convert
     * @return the corresponding domain objects
     */
    private List<PullRequestDomain> convertPullRequestAPIToDomain(List<PullRequestAPI> openPullRequests) {
        List<PullRequestDomain> pullRequestDomains = new LinkedList<>();
        for (PullRequestAPI pullRequestAPI : openPullRequests) {
            PullRequestDomain pullRequestDomain = new PullRequestDomain();
            pullRequestDomain.setNumber(pullRequestAPI.getNumber());
            pullRequestDomain.setTitle(pullRequestAPI.getTitle());
            pullRequestDomain.setAuthor(pullRequestAPI.getAuthor());
            pullRequestDomain.setCreatedAt(pullRequestAPI.getCreatedAt());
            pullRequestDomain.setUrl(pullRequestAPI.getUrl());
            pullRequestDomains.add(pullRequestDomain);
        }
        return pullRequestDomains;
    }

    /**
     * Converts a list of tag API objects to domain objects.
     *
     * @param tags the API objects to convert
     * @return the corresponding domain objects
     */
    private List<TagDomain> convertTagAPIToDomain(List<TagAPI> tags) {
        List<TagDomain> tagDomains = new LinkedList<>();
        for (TagAPI tagAPI : tags) {
            TagDomain tagDomain = new TagDomain();
            tagDomain.setName(tagAPI.getName());
            tagDomain.setCommitSha(tagAPI.getCommitSha());
            tagDomain.setZipballUrl(tagAPI.getZipballUrl());
            tagDomains.add(tagDomain);
        }
        return tagDomains;
    }

    /**
     * Converts a list of workflow API objects to domain objects, marking the
     * ones with a run in progress.
     *
     * @param workflows      the API objects to convert
     * @param runsInProgress the repository's queued and in-progress runs
     * @return the corresponding domain objects
     */
    private List<WorkflowDomain> convertWorkflowAPIToDomain(List<WorkflowAPI> workflows, List<WorkflowRunAPI> runsInProgress) {
        Set<Long> running = new HashSet<>();
        runsInProgress.forEach(run -> running.add(run.getWorkflowId()));

        List<WorkflowDomain> workflowDomains = new LinkedList<>();
        for (WorkflowAPI workflowAPI : workflows) {
            WorkflowDomain workflowDomain = new WorkflowDomain();
            workflowDomain.setId(workflowAPI.getId());
            workflowDomain.setName(workflowAPI.getName());
            workflowDomain.setState(workflowAPI.getState());
            workflowDomain.setPath(workflowAPI.getPath());
            workflowDomain.setUrl(workflowAPI.getUrl());
            workflowDomain.setRunning(running.contains(workflowAPI.getId()));
            workflowDomains.add(workflowDomain);
        }
        return workflowDomains;
    }

    /**
     * Converts a list of branch API objects to domain objects.
     *
     * @param branches the API objects to convert
     * @return the corresponding domain objects
     */
    private List<BranchDomain> convertBranchAPIToDomain(List<BranchAPI> branches) {
        List<BranchDomain> branchDomains = new LinkedList<>();
        for (BranchAPI branchAPI : branches) {
            BranchDomain branchDomain = new BranchDomain();
            branchDomain.setName(branchAPI.getName());
            branchDomain.setCommitSha(branchAPI.getCommitSha());
            branchDomain.setProtectedBranch(branchAPI.isProtectedBranch());
            branchDomains.add(branchDomain);
        }
        return branchDomains;
    }

}
