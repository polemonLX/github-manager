package com.polemon.github_manager.service;

import com.polemon.api_lib.persistence.exception.PersistenceException;
import com.polemon.github_manager.client.GitHubClient;
import com.polemon.github_manager.exceptions.InvalidTokenException;
import com.polemon.github_manager.model.dto.RateLimitDTO;
import com.polemon.github_manager.model.dto.api.RateLimitAPI;
import com.polemon.github_manager.model.entity.User;
import com.polemon.github_manager.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service class for each user's GitHub credentials.
 * This service provides methods to retrieve and store a user's GitHub token,
 * and creates the user's entry the first time they store something.
 */
@Slf4j
@Service
public class AuthService {

    /**
     * Store of the users and their GitHub tokens.
     */
    private final UserRepository userRepository;

    /**
     * Client for validating the GitHub token against the GitHub API.
     */
    private final GitHubClient gitHubClient;

    public AuthService(UserRepository userRepository, GitHubClient gitHubClient) {
        this.userRepository = userRepository;
        this.gitHubClient = gitHubClient;
    }

    /**
     * Retrieves a user's GitHub token.
     *
     * @param userId the user id
     * @return the GitHub token, or {@code null} if the user has none
     */
    public String getGithubToken(String userId) {
        return userRepository.findById(userId).map(User::getGithubToken).orElse(null);
    }

    /**
     * Lists the ids of the users that have a GitHub token, i.e. whose
     * repositories can be synchronized.
     *
     * @return the user ids; empty if no user has a token
     */
    public List<String> getUsersWithToken() {
        return userRepository.getAll().stream()
                .filter(user -> user.getGithubToken() != null)
                .map(User::getId)
                .toList();
    }

    /**
     * Saves a user's GitHub token after validating it against the GitHub API.
     *
     * @param userId      the user id
     * @param githubToken the GitHub token to be saved
     * @throws InvalidTokenException if the token is invalid or expired
     */
    public void setGithubToken(String userId, String githubToken) throws InvalidTokenException {
        if (!gitHubClient.isTokenValid(githubToken))
            throw new InvalidTokenException("GitHub token is invalid or expired");

        saveGithubToken(userId, githubToken);
    }

    /**
     * Retrieves the current GitHub API rate limit of a user's token.
     *
     * @param userId the user id
     * @return the current rate limit status, or {@code null} if the user has no
     *         token or GitHub didn't answer
     */
    public RateLimitDTO getRateLimit(String userId) {
        String token = getGithubToken(userId);
        if (token == null)
            return null;

        RateLimitAPI rateLimitAPI = gitHubClient.getRateLimit(token);
        if (rateLimitAPI == null)
            return null;
        return new RateLimitDTO(rateLimitAPI.remaining(), rateLimitAPI.limit(), rateLimitAPI.resetAt());
    }

    /**
     * Clears a user's stored GitHub token.
     *
     * @param userId the user id
     */
    public void clear(String userId) {
        saveGithubToken(userId, null);
    }

    /**
     * Makes sure a user has an entry, which their repositories and workflows
     * reference. Creates it, without a token, the first time. Two first
     * requests of the same user can race to create it: the one that loses finds
     * it created and carries on.
     *
     * @param userId the user id
     */
    public void ensureUser(String userId) {
        if (userRepository.findById(userId).isPresent())
            return;

        try {
            userRepository.create(new User(userId, null));
            log.debug("Created user {}", userId);
        } catch (PersistenceException e) {
            if (e.getReason() != PersistenceException.Reason.DUPLICATE)
                throw e;
        }
    }

    /**
     * Stores a user's token, creating the user's entry if needed.
     *
     * @param userId      the user id
     * @param githubToken the token, or {@code null} to clear it
     */
    private void saveGithubToken(String userId, String githubToken) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            user.get().setGithubToken(githubToken);
            userRepository.update(user.get());
        } else {
            userRepository.create(new User(userId, githubToken));
        }
    }

}
