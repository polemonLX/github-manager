package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.RepositoryEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryRepositoryTest {

    @SuppressWarnings("unchecked")
    private final IDatasource<RepositoryEntity> datasource = mock(IDatasource.class);
    private final RepositoryRepository repository = new RepositoryRepository(datasource);

    @Test
    void findByIdDelegates() {
        RepositoryEntity entity = new RepositoryEntity("r1", "polemonLX", "auth");
        when(datasource.findById("r1")).thenReturn(Optional.of(entity));

        assertSame(entity, repository.findById("r1").orElseThrow());
    }

    @Test
    void findByOwnerAndNameMatchesBoth() {
        RepositoryEntity auth = new RepositoryEntity("r1", "polemonLX", "auth");
        when(datasource.findAll("owner", "polemonLX")).thenReturn(List.of(new RepositoryEntity("r2", "polemonLX", "parent"), auth));

        assertSame(auth, repository.findByOwnerAndName("polemonLX", "auth").orElseThrow());
        assertTrue(repository.findByOwnerAndName("polemonLX", "missing").isEmpty());
    }

    @Test
    void getAllDelegates() {
        List<RepositoryEntity> repositories = List.of(new RepositoryEntity("r1", "polemonLX", "auth"));
        when(datasource.getAll()).thenReturn(repositories);

        assertEquals(repositories, repository.getAll());
    }

    @Test
    void createUpdateAndDeleteDelegate() {
        RepositoryEntity entity = new RepositoryEntity("r1", "polemonLX", "auth");

        repository.create(entity);
        repository.update(entity);
        repository.deleteById("r1");

        verify(datasource).create(entity);
        verify(datasource).update(entity);
        verify(datasource).deleteById("r1");
    }

}
