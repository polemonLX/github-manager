package com.polemon.github_manager.scheduler;

import com.polemon.github_manager.service.RepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background job that periodically refreshes the cached repository data.
 * This is a thin trigger: it delegates the actual sync logic to the service.
 */
@Slf4j
@Component
public class RepositorySyncJob {

    /**
     * The service used to perform the actual repository sync.
     */
    private final RepositoryService repositoryService;

    public RepositorySyncJob(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * Refreshes every user's repository cache from GitHub.
     * Runs once at startup (initialDelay = 0) to warm the cache, then every
     * {@code github-manager.sync-interval-ms} milliseconds (default 5 minutes) after the
     * previous run completes.
     */
    @Scheduled(fixedDelayString = "${github-manager.sync-interval-ms}", initialDelay = 0)
    public void sync() {
        log.debug("Starting scheduled repository sync");
        repositoryService.syncAll();
        log.debug("Scheduled repository sync completed");
    }

}
