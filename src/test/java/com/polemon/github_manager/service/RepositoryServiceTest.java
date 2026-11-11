package com.polemon.github_manager.service;

import com.polemon.api_lib.persistence.exception.PersistenceException;
import com.polemon.github_manager.client.GitHubClient;
import com.polemon.github_manager.exceptions.InvalidRepositoryException;
import com.polemon.github_manager.model.domain.BranchDomain;
import com.polemon.github_manager.model.domain.PullRequestDomain;
import com.polemon.github_manager.model.domain.TagDomain;
import com.polemon.github_manager.model.domain.WorkflowDomain;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.model.dto.api.*;
import com.polemon.github_manager.model.dto.RepositoryGroupDTO;
import com.polemon.github_manager.model.entity.RepositoryEntity;
import com.polemon.github_manager.model.entity.RepositoryGroup;
import com.polemon.github_manager.model.entity.RepositoryGroupMember;
import com.polemon.github_manager.model.entity.TrackedRepository;
import com.polemon.github_manager.repository.RepositoryGroupMemberRepository;
import com.polemon.github_manager.repository.RepositoryGroupRepository;
import com.polemon.github_manager.repository.RepositoryRepository;
import com.polemon.github_manager.repository.TrackedRepositoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RepositoryServiceTest {

    private final RepositoryRepository repositoryRepository = mock(RepositoryRepository.class);
    private final TrackedRepositoryRepository trackedRepositoryRepository = mock(TrackedRepositoryRepository.class);
    private final GitHubClient gitHubClient = mock(GitHubClient.class);
    private final AuthService authService = mock(AuthService.class);
    private final RepositoryGroupRepository groupRepository = mock(RepositoryGroupRepository.class);
    private final RepositoryGroupMemberRepository groupMemberRepository = mock(RepositoryGroupMemberRepository.class);
    private final RepositoryService repositoryService = new RepositoryService(repositoryRepository, trackedRepositoryRepository,
            groupRepository, groupMemberRepository, gitHubClient, authService);

    /**
     * u1 tracks auth (link l1, groups g1 and g2) and portal (link l2, group g1)
     * and server (link l3, no group); g3 is empty.
     */
    private void givenGroups() {
        when(groupRepository.findByUser("u1")).thenReturn(List.of(new RepositoryGroup("g2", "u1", "libs"),
                new RepositoryGroup("g1", "u1", "Apps"), new RepositoryGroup("g3", "u1", "empty")));
        when(groupMemberRepository.findByGroup("g1")).thenReturn(List.of(new RepositoryGroupMember("m1", "g1", "l1"),
                new RepositoryGroupMember("m2", "g1", "l2")));
        when(groupMemberRepository.findByGroup("g2")).thenReturn(List.of(new RepositoryGroupMember("m3", "g2", "l1")));
        when(groupMemberRepository.findByGroup("g3")).thenReturn(List.of());
        when(trackedRepositoryRepository.findByUser("u1")).thenReturn(List.of(new TrackedRepository("l2", "u1", "r2"),
                new TrackedRepository("l1", "u1", "r1"), new TrackedRepository("l3", "u1", "r3"), new TrackedRepository("l4", "u1", "gone")));
        when(repositoryRepository.findById("r1")).thenReturn(Optional.of(repository("r1", "auth")));
        when(repositoryRepository.findById("r2")).thenReturn(Optional.of(repository("r2", "portal")));
        when(repositoryRepository.findById("r3")).thenReturn(Optional.of(repository("r3", "server")));
        when(repositoryRepository.findById("gone")).thenReturn(Optional.empty());
    }

    @Test
    void listsEveryRepositoryWithItsGroups() {
        givenGroups();

        List<RepositoryDTO> repositories = repositoryService.getRepositories("u1", null);

        assertEquals(List.of("auth", "portal", "server"), repositories.stream().map(RepositoryDTO::getName).toList());
        assertEquals(java.util.Set.of("g1", "g2"), java.util.Set.copyOf(repositories.get(0).getGroupIds()));
        assertEquals(List.of("g1"), repositories.get(1).getGroupIds());
        assertEquals(List.of(), repositories.get(2).getGroupIds());
    }

    @Test
    void listsOnlyTheRepositoriesOfAGroup() {
        givenGroups();

        assertEquals(List.of("auth", "portal"), repositoryService.getRepositories("u1", "g1").stream().map(RepositoryDTO::getName).toList());
        assertEquals(List.of("auth"), repositoryService.getRepositories("u1", "g2").stream().map(RepositoryDTO::getName).toList());
        assertEquals(List.of(), repositoryService.getRepositories("u1", "g3"));
    }

    @Test
    void aGroupThatIsNotTheUsersShowsEveryRepository() {
        givenGroups();

        assertEquals(3, repositoryService.getRepositories("u1", "someone-elses").size());
    }

    @Test
    void listsTheGroupsByName() {
        givenGroups();

        assertEquals(List.of(new RepositoryGroupDTO("g1", "Apps"), new RepositoryGroupDTO("g3", "empty"), new RepositoryGroupDTO("g2", "libs")),
                repositoryService.getGroups("u1"));
    }

    @Test
    void createsAGroupWithATrimmedName() {
        RepositoryGroupDTO group = repositoryService.createGroup("u1", "  Apps ");

        ArgumentCaptor<RepositoryGroup> stored = ArgumentCaptor.forClass(RepositoryGroup.class);
        verify(groupRepository).create(stored.capture());
        assertEquals("u1", stored.getValue().getUserId());
        assertEquals("Apps", stored.getValue().getName());
        assertNotNull(stored.getValue().getId());
        assertEquals(new RepositoryGroupDTO(stored.getValue().getId(), "Apps"), group);
    }

    @Test
    void refusesEmptyOrLongGroupNames() {
        assertThrows(IllegalArgumentException.class, () -> repositoryService.createGroup("u1", null));
        assertThrows(IllegalArgumentException.class, () -> repositoryService.createGroup("u1", "   "));
        assertThrows(IllegalArgumentException.class,
                () -> repositoryService.createGroup("u1", "x".repeat(RepositoryService.MAX_GROUP_NAME + 1)));
        repositoryService.createGroup("u1", "x".repeat(RepositoryService.MAX_GROUP_NAME));
        verify(groupRepository, times(1)).create(any());
    }

    @Test
    void refusesADuplicateGroupName() {
        doThrow(duplicate()).when(groupRepository).create(any());

        assertEquals("A group named 'Apps' already exists",
                assertThrows(IllegalArgumentException.class, () -> repositoryService.createGroup("u1", "Apps")).getMessage());
    }

    @Test
    void otherFailuresCreatingAGroupPropagate() {
        PersistenceException failure = new PersistenceException(PersistenceException.Reason.FAILURE, "down", null);
        doThrow(failure).when(groupRepository).create(any());

        assertSame(failure, assertThrows(PersistenceException.class, () -> repositoryService.createGroup("u1", "Apps")));
    }

    @Test
    void settingTheGroupsJoinsTheNewOnesAndLeavesTheOthers() {
        givenGroups();
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(repository("r1", "auth")));
        when(groupMemberRepository.findByTrackedRepository("l1")).thenReturn(List.of(new RepositoryGroupMember("m1", "g1", "l1"),
                new RepositoryGroupMember("m3", "g2", "l1")));

        repositoryService.setGroups("u1", "polemonLX", "auth", List.of("g2", "g3"));

        verify(groupMemberRepository).deleteById("m1");
        verify(groupMemberRepository, never()).deleteById("m3");
        ArgumentCaptor<RepositoryGroupMember> created = ArgumentCaptor.forClass(RepositoryGroupMember.class);
        verify(groupMemberRepository).create(created.capture());
        assertEquals("g3", created.getValue().getGroupId());
        assertEquals("l1", created.getValue().getTrackedRepositoryId());
        assertNotNull(created.getValue().getId());
    }

    @Test
    void noGroupsLeavesEveryGroup() {
        givenGroups();
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(repository("r1", "auth")));
        when(groupMemberRepository.findByTrackedRepository("l1")).thenReturn(List.of(new RepositoryGroupMember("m1", "g1", "l1")));

        repositoryService.setGroups("u1", "polemonLX", "auth", null);

        verify(groupMemberRepository).deleteById("m1");
        verify(groupMemberRepository, never()).create(any());
    }

    @Test
    void settingTheGroupsOfAnUntrackedRepositoryFails() {
        givenGroups();
        when(repositoryRepository.findByOwnerAndName("polemonLX", "other")).thenReturn(Optional.of(repository("r9", "other")));

        assertThrows(IllegalArgumentException.class, () -> repositoryService.setGroups("u1", "polemonLX", "other", List.of("g1")));
        assertThrows(IllegalArgumentException.class, () -> repositoryService.setGroups("u1", "polemonLX", "missing", List.of("g1")));
        verify(groupMemberRepository, never()).create(any());
    }

    @Test
    void settingAGroupThatIsNotTheUsersFails() {
        givenGroups();
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(repository("r1", "auth")));

        assertEquals("Unknown group", assertThrows(IllegalArgumentException.class,
                () -> repositoryService.setGroups("u1", "polemonLX", "auth", List.of("g1", "foreign"))).getMessage());
        verify(groupMemberRepository, never()).create(any());
        verify(groupMemberRepository, never()).deleteById(any());
    }

    @Test
    void addingARepositoryNobodyTracksStoresSyncsAndLinksIt() throws InvalidRepositoryException {
        when(authService.getGithubToken("u1")).thenReturn("t1");
        when(gitHubClient.repositoryExists("polemonLX", "auth", "t1")).thenReturn(true);
        when(gitHubClient.getBranches("polemonLX", "auth", "t1")).thenReturn(List.of(new BranchAPI("main", "a", true)));

        repositoryService.addRepository("u1", new RepositoryDTO("polemonLX", "auth"));

        ArgumentCaptor<RepositoryEntity> created = ArgumentCaptor.forClass(RepositoryEntity.class);
        verify(repositoryRepository).create(created.capture());
        assertEquals("polemonLX", created.getValue().getOwner());
        assertEquals("auth", created.getValue().getName());
        verify(repositoryRepository).update(created.getValue());
        assertEquals("main", created.getValue().getBranches().getFirst().getName());

        ArgumentCaptor<TrackedRepository> link = ArgumentCaptor.forClass(TrackedRepository.class);
        verify(trackedRepositoryRepository).create(link.capture());
        assertNotNull(link.getValue().getId());
        assertEquals("u1", link.getValue().getUserId());
        assertEquals(created.getValue().getId(), link.getValue().getRepositoryId());
    }

    @Test
    void addingARepositoryAnotherUserTracksOnlyLinksIt() throws InvalidRepositoryException {
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(repository("r1", "auth")));
        when(trackedRepositoryRepository.findByUser("u2")).thenReturn(List.of());
        when(authService.getGithubToken("u2")).thenReturn("t2");
        when(gitHubClient.repositoryExists("polemonLX", "auth", "t2")).thenReturn(true);

        repositoryService.addRepository("u2", new RepositoryDTO("polemonLX", "auth"));

        verify(repositoryRepository, never()).create(any());
        verify(gitHubClient, never()).getTags(anyString(), anyString(), anyString());
        ArgumentCaptor<TrackedRepository> link = ArgumentCaptor.forClass(TrackedRepository.class);
        verify(trackedRepositoryRepository).create(link.capture());
        assertEquals("u2", link.getValue().getUserId());
        assertEquals("r1", link.getValue().getRepositoryId());
    }

    @Test
    void addingARepositoryStoredMeanwhileUsesThatEntry() throws InvalidRepositoryException {
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth"))
                .thenReturn(Optional.empty(), Optional.of(repository("r9", "auth")));
        doThrow(duplicate()).when(repositoryRepository).create(any());
        when(authService.getGithubToken("u1")).thenReturn("t1");
        when(gitHubClient.repositoryExists("polemonLX", "auth", "t1")).thenReturn(true);

        repositoryService.addRepository("u1", new RepositoryDTO("polemonLX", "auth"));

        ArgumentCaptor<TrackedRepository> link = ArgumentCaptor.forClass(TrackedRepository.class);
        verify(trackedRepositoryRepository).create(link.capture());
        assertEquals("r9", link.getValue().getRepositoryId());
        verify(repositoryRepository, never()).update(any());
    }

    @Test
    void addingARepositoryPropagatesOtherStorageFailures() {
        doThrow(new PersistenceException("down")).when(repositoryRepository).create(any());
        when(authService.getGithubToken("u1")).thenReturn("t1");
        when(gitHubClient.repositoryExists("polemonLX", "auth", "t1")).thenReturn(true);

        assertThrows(PersistenceException.class, () -> repositoryService.addRepository("u1", new RepositoryDTO("polemonLX", "auth")));
    }

    @Test
    void addingARepositoryTheUserAlreadyTracksFails() {
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(repository("r1", "auth")));
        when(trackedRepositoryRepository.findByUser("u1")).thenReturn(List.of(new TrackedRepository("l1", "u1", "r1")));

        assertThrows(InvalidRepositoryException.class, () -> repositoryService.addRepository("u1", new RepositoryDTO("polemonLX", "auth")));
        verify(trackedRepositoryRepository, never()).create(any());
    }

    @Test
    void aLinkCreatedMeanwhileIsReportedAsAlreadyTracked() {
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(repository("r1", "auth")));
        when(authService.getGithubToken("u1")).thenReturn("t1");
        when(gitHubClient.repositoryExists("polemonLX", "auth", "t1")).thenReturn(true);
        doThrow(duplicate()).when(trackedRepositoryRepository).create(any());

        assertThrows(InvalidRepositoryException.class, () -> repositoryService.addRepository("u1", new RepositoryDTO("polemonLX", "auth")));
    }

    @Test
    void aLinkStorageFailurePropagates() {
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(repository("r1", "auth")));
        when(authService.getGithubToken("u1")).thenReturn("t1");
        when(gitHubClient.repositoryExists("polemonLX", "auth", "t1")).thenReturn(true);
        doThrow(new PersistenceException("down")).when(trackedRepositoryRepository).create(any());

        assertThrows(PersistenceException.class, () -> repositoryService.addRepository("u1", new RepositoryDTO("polemonLX", "auth")));
    }

    @Test
    void addingARepositoryNeedsATokenThatReachesIt() {
        assertThrows(InvalidRepositoryException.class, () -> repositoryService.addRepository("u1", new RepositoryDTO("polemonLX", "auth")));

        when(authService.getGithubToken("u1")).thenReturn("t1");
        assertThrows(InvalidRepositoryException.class, () -> repositoryService.addRepository("u1", new RepositoryDTO("polemonLX", "private")));

        verify(repositoryRepository, never()).create(any());
        verify(trackedRepositoryRepository, never()).create(any());
    }

    @Test
    void getRepositoriesReadsTheUsersRepositoriesFromTheDatabase() {
        RepositoryEntity auth = repository("r1", "auth");
        auth.setBranches(List.of(new BranchDomain("main", "a", true), new BranchDomain("feature/x", "b", false),
                new BranchDomain("bugfix/y", "c", false), new BranchDomain("release/1", "d", false), new BranchDomain("dev", "e", false)));
        auth.setPullRequests(List.of(new PullRequestDomain(1, "title", "me", Instant.EPOCH, "url")));
        auth.setReleases(List.of(new TagDomain("1.0.0", "sha", "zip")));
        auth.setWorkflows(List.of(new WorkflowDomain(1, "release", "active", "path", "url")));
        auth.setActionsRunning(2);
        auth.setLastSyncedAt(1_000);
        RepositoryEntity api = repository("r2", "api-lib");
        when(trackedRepositoryRepository.findByUser("u1")).thenReturn(List.of(
                new TrackedRepository("l1", "u1", "r1"), new TrackedRepository("l2", "u1", "r2"), new TrackedRepository("l3", "u1", "gone")));
        when(repositoryRepository.findById("r1")).thenReturn(Optional.of(auth));
        when(repositoryRepository.findById("r2")).thenReturn(Optional.of(api));

        List<RepositoryDTO> repositories = repositoryService.getRepositories("u1");

        assertEquals(List.of("api-lib", "auth"), repositories.stream().map(RepositoryDTO::getName).toList());
        assertNull(repositories.getFirst().getLastSyncedAt());
        RepositoryDTO dto = repositories.get(1);
        assertEquals(5, dto.getAllBranches().size());
        assertEquals(1, dto.getFeatureBranches().size());
        assertEquals(1, dto.getBugfixBranches().size());
        assertEquals("dev", dto.getOtherBranches().getFirst().getName());
        assertEquals(1, dto.getOpenPullRequests().size());
        assertEquals(2, dto.getActionsRunning().size());
        assertEquals("1.0.0", dto.getReleases().getFirst().getName());
        assertEquals("release", dto.getWorkflows().getFirst().getName());
        assertNotNull(dto.getLastSyncedAt());
        verifyNoInteractions(gitHubClient);
    }

    @Test
    void removingTheLastUserDeletesTheRepository() {
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(repository("r1", "auth")));
        when(trackedRepositoryRepository.findByRepository("r1")).thenReturn(List.of(new TrackedRepository("l1", "u1", "r1")));

        repositoryService.removeRepository("u1", new RepositoryDTO("polemonLX", "auth"));
        repositoryService.removeRepository("u1", new RepositoryDTO("polemonLX", "missing"));

        verify(trackedRepositoryRepository).deleteById("l1");
        verify(repositoryRepository).deleteById("r1");
    }

    @Test
    void removingKeepsARepositoryOthersTrack() {
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(repository("r1", "auth")));
        when(trackedRepositoryRepository.findByRepository("r1")).thenReturn(List.of(
                new TrackedRepository("l1", "u1", "r1"), new TrackedRepository("l2", "u2", "r1")));

        repositoryService.removeRepository("u1", new RepositoryDTO("polemonLX", "auth"));

        verify(trackedRepositoryRepository).deleteById("l1");
        verify(trackedRepositoryRepository, never()).deleteById("l2");
        verify(repositoryRepository, never()).deleteById(anyString());
    }

    @Test
    void syncAllUsesATokenOfAUserWhoTracksEachRepository() {
        RepositoryEntity auth = repository("r1", "auth");
        RepositoryEntity orphan = repository("r2", "orphan");
        RepositoryEntity failing = repository("r3", "failing");
        when(repositoryRepository.getAll()).thenReturn(List.of(failing, auth, orphan));
        when(trackedRepositoryRepository.findByRepository("r1")).thenReturn(List.of(
                new TrackedRepository("l1", "u1", "r1"), new TrackedRepository("l2", "u2", "r1")));
        when(trackedRepositoryRepository.findByRepository("r3")).thenReturn(List.of(new TrackedRepository("l3", "u2", "r3")));
        when(authService.getGithubToken("u2")).thenReturn("t2");
        when(gitHubClient.getTags("polemonLX", "failing", "t2")).thenThrow(new IllegalStateException("GitHub down"));

        repositoryService.syncAll();

        verify(gitHubClient).getTags("polemonLX", "auth", "t2");
        verify(repositoryRepository).update(auth);
        verify(repositoryRepository, never()).update(orphan);
        verify(repositoryRepository, never()).update(failing);
        assertTrue(auth.getLastSyncedAt() > 0);
    }

    @Test
    void syncStoresWhatGitHubReturnsWithTheUsersToken() {
        RepositoryEntity auth = repository("r1", "auth");
        trackedBy("u1", auth);
        when(authService.getGithubToken("u1")).thenReturn("t1");
        when(gitHubClient.getTags("polemonLX", "auth", "t1")).thenReturn(List.of(new TagAPI("v1.0.0", "a", "zip"), new TagAPI("1.1.0", "b", "zip")));
        when(gitHubClient.getActionsRunning("polemonLX", "auth", "t1")).thenReturn(List.of(new WorkflowRunAPI()));
        when(gitHubClient.getOpenPullRequests("polemonLX", "auth", "t1")).thenReturn(List.of(new PullRequestAPI(1, "title", "me", Instant.EPOCH, "url")));
        when(gitHubClient.getWorkflows("polemonLX", "auth", "t1")).thenReturn(List.of(new WorkflowAPI(1, "release", "active", "path", "url")));

        repositoryService.sync("u1");

        assertEquals(List.of("1.1.0", "1.0.0"), auth.getReleases().stream().map(TagDomain::getName).toList());
        assertEquals(1, auth.getActionsRunning());
        assertEquals(1, auth.getPullRequests().size());
        assertEquals("release", auth.getWorkflows().getFirst().getName());
        verify(repositoryRepository).update(auth);
    }

    @Test
    void syncOfOneRepositoryOnlyCoversOneTheUserTracks() {
        RepositoryEntity auth = repository("r1", "auth");
        trackedBy("u1", auth);
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(auth));
        when(authService.getGithubToken("u1")).thenReturn("t1");
        when(authService.getGithubToken("u2")).thenReturn("t2");

        repositoryService.sync("u1", "polemonLX", "auth");
        repositoryService.sync("u2", "polemonLX", "auth");

        verify(gitHubClient).getTags("polemonLX", "auth", "t1");
        verify(gitHubClient, never()).getTags("polemonLX", "auth", "t2");
    }

    @Test
    void syncPullRequestStoresTheOpenPullRequests() {
        RepositoryEntity auth = repository("r1", "auth");
        trackedBy("u1", auth);
        when(repositoryRepository.findByOwnerAndName("polemonLX", "auth")).thenReturn(Optional.of(auth));
        when(authService.getGithubToken("u1")).thenReturn("t1");
        when(gitHubClient.getOpenPullRequests("polemonLX", "auth", "t1")).thenReturn(List.of(new PullRequestAPI(1, "title", "me", Instant.EPOCH, "url")));

        repositoryService.syncPullRequest("u1");
        repositoryService.syncPullRequest("u1", "polemonLX", "auth");
        repositoryService.syncPullRequest("u1", "polemonLX", "missing");

        assertEquals(1, auth.getPullRequests().size());
        verify(repositoryRepository, times(2)).update(auth);
    }

    @Test
    void clearUntracksEveryRepositoryOfTheUser() {
        when(trackedRepositoryRepository.findByUser("u1")).thenReturn(List.of(new TrackedRepository("l1", "u1", "r1")));
        when(trackedRepositoryRepository.findByRepository("r1")).thenReturn(List.of(new TrackedRepository("l1", "u1", "r1")));

        repositoryService.clear("u1");

        verify(trackedRepositoryRepository).deleteById("l1");
        verify(repositoryRepository).deleteById("r1");
    }

    private void trackedBy(String userId, RepositoryEntity repository) {
        when(trackedRepositoryRepository.findByUser(userId)).thenReturn(List.of(new TrackedRepository("l-" + userId, userId, repository.getId())));
        when(repositoryRepository.findById(repository.getId())).thenReturn(Optional.of(repository));
    }

    private static RepositoryEntity repository(String id, String name) {
        return new RepositoryEntity(id, "polemonLX", name);
    }

    private static PersistenceException duplicate() {
        return new PersistenceException(PersistenceException.Reason.DUPLICATE, "duplicate", null);
    }

}
