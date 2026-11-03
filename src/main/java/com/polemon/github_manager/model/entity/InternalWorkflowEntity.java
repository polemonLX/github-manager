package com.polemon.github_manager.model.entity;

import com.polemon.api_lib.persistence.annotation.OnDelete;
import com.polemon.api_lib.persistence.annotation.Reference;
import com.polemon.api_lib.persistence.annotation.Store;
import com.polemon.api_lib.persistence.annotation.Unique;
import com.polemon.api_lib.persistence.model.Entity;
import lombok.*;

import java.util.LinkedList;
import java.util.List;

/**
 * A user-defined workflow: a named, ordered sequence of {@link InternalWorkflowStageEntity}
 * stages. The name identifies the workflow among its user's workflows for
 * update and delete operations.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Store("workflows")
@Unique({"userId", "name"})
public class InternalWorkflowEntity extends Entity {

    /**
     * The user who owns the workflow; their workflows go when they are deleted.
     */
    @Reference(value = User.class, onDelete = OnDelete.CASCADE)
    private String userId;

    /**
     * The workflow's name, unique per user.
     */
    private String name;

    /**
     * The ordered stages that make up the workflow (stored as JSON).
     */
    private List<InternalWorkflowStageEntity> stages = new LinkedList<>();

    public InternalWorkflowEntity(String name, List<InternalWorkflowStageEntity> stages) {
        this.name = name;
        this.stages = stages;
    }

}
