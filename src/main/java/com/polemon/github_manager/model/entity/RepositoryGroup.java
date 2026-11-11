package com.polemon.github_manager.model.entity;

import com.polemon.api_lib.persistence.annotation.OnDelete;
import com.polemon.api_lib.persistence.annotation.Reference;
import com.polemon.api_lib.persistence.annotation.Store;
import com.polemon.api_lib.persistence.annotation.Unique;
import com.polemon.api_lib.persistence.model.Entity;
import lombok.*;

/**
 * A group a user defines to organise the repositories they track. Each user
 * has their own groups; a repository can be in several of them (see
 * {@link RepositoryGroupMember}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Store("repository_groups")
@Unique({"userId", "name"})
public class RepositoryGroup extends Entity {

    /**
     * The user the group belongs to.
     */
    @Reference(value = User.class, onDelete = OnDelete.CASCADE)
    private String userId;

    /**
     * The group's name, unique per user.
     */
    private String name;

    public RepositoryGroup(String id, String userId, String name) {
        super(id);
        this.userId = userId;
        this.name = name;
    }

}
