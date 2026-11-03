package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.InternalWorkflowEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stored {@link InternalWorkflowEntity internal workflows}, per user.
 */
@Repository
public class WorkflowRepository {

    /**
     * The datasource holding the workflows.
     */
    private final IDatasource<InternalWorkflowEntity> datasource;

    public WorkflowRepository(IDatasource<InternalWorkflowEntity> datasource) {
        this.datasource = datasource;
    }

    /**
     * Lists a user's workflows.
     *
     * @param userId the user id
     * @return the user's workflows; empty if there are none
     */
    public List<InternalWorkflowEntity> findByUser(String userId) {
        return datasource.findAll("userId", userId);
    }

    /**
     * Stores a new workflow.
     *
     * @param workflow the workflow, with its id and user id set
     */
    public void create(InternalWorkflowEntity workflow) {
        datasource.create(workflow);
    }

    /**
     * Replaces a stored workflow.
     *
     * @param workflow the workflow, with the id of the entry to replace
     */
    public void update(InternalWorkflowEntity workflow) {
        datasource.update(workflow);
    }

    /**
     * Deletes a workflow.
     *
     * @param id the id of the entry
     */
    public void deleteById(String id) {
        datasource.deleteById(id);
    }

}
