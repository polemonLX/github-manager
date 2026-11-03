package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.InternalWorkflowEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRepositoryTest {

    @SuppressWarnings("unchecked")
    private final IDatasource<InternalWorkflowEntity> datasource = mock(IDatasource.class);
    private final WorkflowRepository repository = new WorkflowRepository(datasource);

    @Test
    void findByUserLooksUpByUserId() {
        List<InternalWorkflowEntity> workflows = List.of(new InternalWorkflowEntity("release", List.of()));
        when(datasource.findAll("userId", "u1")).thenReturn(workflows);

        assertEquals(workflows, repository.findByUser("u1"));
    }

    @Test
    void createUpdateAndDeleteDelegate() {
        InternalWorkflowEntity workflow = new InternalWorkflowEntity("release", List.of());

        repository.create(workflow);
        repository.update(workflow);
        repository.deleteById("w1");

        verify(datasource).create(workflow);
        verify(datasource).update(workflow);
        verify(datasource).deleteById("w1");
    }

}
