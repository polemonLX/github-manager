package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.TrackedRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackedRepositoryRepositoryTest {

    @SuppressWarnings("unchecked")
    private final IDatasource<TrackedRepository> datasource = mock(IDatasource.class);
    private final TrackedRepositoryRepository repository = new TrackedRepositoryRepository(datasource);

    @Test
    void findByUserAndByRepositoryLookUpTheirField() {
        List<TrackedRepository> byUser = List.of(new TrackedRepository("l1", "u1", "r1"));
        List<TrackedRepository> byRepository = List.of(new TrackedRepository("l2", "u2", "r2"));
        when(datasource.findAll("userId", "u1")).thenReturn(byUser);
        when(datasource.findAll("repositoryId", "r2")).thenReturn(byRepository);

        assertEquals(byUser, repository.findByUser("u1"));
        assertEquals(byRepository, repository.findByRepository("r2"));
    }

    @Test
    void createAndDeleteDelegate() {
        TrackedRepository link = new TrackedRepository("l1", "u1", "r1");

        repository.create(link);
        repository.deleteById("l1");

        verify(datasource).create(link);
        verify(datasource).deleteById("l1");
    }

}
