package com.polemon.github_manager.model.entity;

import com.polemon.api_lib.persistence.annotation.OnDelete;
import com.polemon.api_lib.persistence.annotation.Reference;
import com.polemon.api_lib.persistence.annotation.Store;
import com.polemon.api_lib.persistence.annotation.Unique;
import com.polemon.api_lib.persistence.model.Entity;
import lombok.*;

/**
 * Puts one of a user's tracked repositories in one of their groups
 * (many-to-many between {@link RepositoryGroup} and {@link TrackedRepository}).
 * It goes when the group is deleted or the user stops tracking the
 * repository.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Store("repository_group_members")
@Unique({"groupId", "trackedRepositoryId"})
public class RepositoryGroupMember extends Entity {

    /**
     * The group.
     */
    @Reference(value = RepositoryGroup.class, onDelete = OnDelete.CASCADE)
    private String groupId;

    /**
     * The user's link to the repository in the group.
     */
    @Reference(value = TrackedRepository.class, onDelete = OnDelete.CASCADE)
    private String trackedRepositoryId;

    public RepositoryGroupMember(String id, String groupId, String trackedRepositoryId) {
        super(id);
        this.groupId = groupId;
        this.trackedRepositoryId = trackedRepositoryId;
    }

}
