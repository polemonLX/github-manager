package com.polemon.github_manager.model.entity;

import com.polemon.api_lib.persistence.annotation.OnDelete;
import com.polemon.api_lib.persistence.annotation.Reference;
import com.polemon.api_lib.persistence.annotation.Store;
import com.polemon.api_lib.persistence.annotation.Unique;
import com.polemon.api_lib.persistence.model.Entity;
import lombok.*;

/**
 * Links a user to a repository they track (many-to-many between {@link User}
 * and {@link RepositoryEntity}). It goes when either side is deleted.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Store("user_repositories")
@Unique({"userId", "repositoryId"})
public class TrackedRepository extends Entity {

    /**
     * The user who tracks the repository.
     */
    @Reference(value = User.class, onDelete = OnDelete.CASCADE)
    private String userId;

    /**
     * The tracked repository.
     */
    @Reference(value = RepositoryEntity.class, onDelete = OnDelete.CASCADE)
    private String repositoryId;

    public TrackedRepository(String id, String userId, String repositoryId) {
        super(id);
        this.userId = userId;
        this.repositoryId = repositoryId;
    }

}
