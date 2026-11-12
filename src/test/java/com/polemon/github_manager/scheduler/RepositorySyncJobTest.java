package com.polemon.github_manager.scheduler;

import com.polemon.github_manager.service.RepositoryService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RepositorySyncJobTest {

    @Test
    void syncsEveryRepository() {
        RepositoryService repositoryService = mock(RepositoryService.class);

        new RepositorySyncJob(repositoryService).sync();

        verify(repositoryService).syncAll();
    }

}
