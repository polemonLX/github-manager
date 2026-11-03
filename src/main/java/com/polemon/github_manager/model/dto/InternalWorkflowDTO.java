package com.polemon.github_manager.model.dto;

import lombok.*;

import java.util.LinkedList;
import java.util.List;

/**
 * A user-defined workflow: a named, ordered sequence of {@link InternalWorkflowStageDTO}
 * stages persisted to the configuration file. The name serves as the workflow's
 * identity for update and delete operations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InternalWorkflowDTO {

    /**
     * The workflow's name, used as its identifier.
     */
    private String name;

    /**
     * The ordered stages that make up the workflow.
     */
    private List<InternalWorkflowStageDTO> stages = new LinkedList<>();

}
