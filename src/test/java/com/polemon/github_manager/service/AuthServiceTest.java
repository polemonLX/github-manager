package com.polemon.github_manager.service;

import com.polemon.api_lib.persistence.exception.PersistenceException;
import com.polemon.github_manager.client.GitHubClient;
import com.polemon.github_manager.exceptions.InvalidTokenException;
import com.polemon.github_manager.model.dto.RateLimitDTO;
import com.polemon.github_manager.model.dto.api.RateLimitAPI;
import com.polemon.github_manager.model.entity.User;
import com.polemon.github_manager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final GitHubClient gitHubClient = mock(GitHubClient.class);
    private final AuthService authService = new AuthService(userRepository, gitHubClient);

    @Test
    void getGithubTokenIsTheUsersOwn() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(new User("u1", "t1")));

        assertEquals("t1", authService.getGithubToken("u1"));
        assertNull(authService.getGithubToken("unknown"));
    }

    @Test
    void getUsersWithTokenSkipsUsersWithoutOne() {
        when(userRepository.getAll()).thenReturn(List.of(new User("u1", "t1"), new User("u2", null), new User("u3", "t3")));

        assertEquals(List.of("u1", "u3"), authService.getUsersWithToken());
    }

    @Test
    void setGithubTokenRejectsAnInvalidToken() {
        when(gitHubClient.isTokenValid("bad")).thenReturn(false);

        assertThrows(InvalidTokenException.class, () -> authService.setGithubToken("u1", "bad"));
        verify(userRepository, never()).create(any());
        verify(userRepository, never()).update(any());
    }

    @Test
    void setGithubTokenCreatesTheUserTheFirstTime() throws InvalidTokenException {
        when(gitHubClient.isTokenValid("t1")).thenReturn(true);

        authService.setGithubToken("u1", "t1");

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(userRepository).create(created.capture());
        assertEquals("u1", created.getValue().getId());
        assertEquals("t1", created.getValue().getGithubToken());
    }

    @Test
    void setGithubTokenReplacesTheStoredOne() throws InvalidTokenException {
        User user = new User("u1", "old");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(gitHubClient.isTokenValid("new")).thenReturn(true);

        authService.setGithubToken("u1", "new");

        assertEquals("new", user.getGithubToken());
        verify(userRepository).update(user);
    }

    @Test
    void clearRemovesTheToken() {
        User user = new User("u1", "t1");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        authService.clear("u1");

        assertNull(user.getGithubToken());
        verify(userRepository).update(user);
    }

    @Test
    void getRateLimitUsesTheUsersToken() {
        Instant reset = Instant.parse("2026-11-03T00:00:00Z");
        when(userRepository.findById("u1")).thenReturn(Optional.of(new User("u1", "t1")));
        when(gitHubClient.getRateLimit("t1")).thenReturn(new RateLimitAPI(4000, 5000, reset));

        RateLimitDTO rateLimit = authService.getRateLimit("u1");

        assertEquals(4000, rateLimit.getRemaining());
        assertEquals(5000, rateLimit.getLimit());
        assertSame(reset, rateLimit.getResetAt());
    }

    @Test
    void getRateLimitIsNullWithoutATokenOrAnAnswer() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(new User("u1", "t1")));

        assertNull(authService.getRateLimit("u1"));
        assertNull(authService.getRateLimit("unknown"));
        verify(gitHubClient).getRateLimit("t1");
    }

    @Test
    void ensureUserCreatesTheUserOnlyOnce() {
        authService.ensureUser("u1");
        verify(userRepository).create(any(User.class));

        when(userRepository.findById("u2")).thenReturn(Optional.of(new User("u2", null)));
        authService.ensureUser("u2");
        verify(userRepository).create(any(User.class));
    }

    @Test
    void ensureUserToleratesAConcurrentCreation() {
        doThrow(new PersistenceException(PersistenceException.Reason.DUPLICATE, "duplicate", null))
                .when(userRepository).create(any(User.class));

        authService.ensureUser("u1");
    }

    @Test
    void ensureUserPropagatesOtherStorageFailures() {
        doThrow(new PersistenceException("down")).when(userRepository).create(any(User.class));

        assertThrows(PersistenceException.class, () -> authService.ensureUser("u1"));
    }

}
