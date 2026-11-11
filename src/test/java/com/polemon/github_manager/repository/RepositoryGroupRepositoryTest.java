package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.RepositoryGroup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryGroupRepositoryTest {

    @SuppressWarnings("unchecked")
    private final IDatasource<RepositoryGroup> datasource = mock(IDatasource.class);
    private final RepositoryGroupRepository repository = new RepositoryGroupRepository(datasource);

    @Test
    void readsDelegate() {
        RepositoryGroup group = new RepositoryGroup("g1", "u1", "Apps");
        when(datasource.findAll("userId", "u1")).thenReturn(List.of(group));
        when(datasource.findById("g1")).thenReturn(Optional.of(group));

        assertEquals(List.of(group), repository.findByUser("u1"));
        assertSame(group, repository.findById("g1").orElseThrow());
    }

    @Test
    void createDelegates() {
        RepositoryGroup group = new RepositoryGroup("g1", "u1", "Apps");

        repository.create(group);

        verify(datasource).create(group);
    }

    @Test
    void deleteDelegates() {
        repository.deleteById("g1");

        verify(datasource).deleteById("g1");
    }

}
