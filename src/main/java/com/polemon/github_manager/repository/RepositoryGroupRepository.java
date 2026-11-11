package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.RepositoryGroup;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Stored {@link RepositoryGroup repository groups}.
 */
@Repository
public class RepositoryGroupRepository {

    /**
     * The datasource holding the groups.
     */
    private final IDatasource<RepositoryGroup> datasource;

    public RepositoryGroupRepository(IDatasource<RepositoryGroup> datasource) {
        this.datasource = datasource;
    }

    /**
     * Lists a user's groups.
     *
     * @param userId the user id
     * @return the user's groups; empty if there are none
     */
    public List<RepositoryGroup> findByUser(String userId) {
        return datasource.findAll("userId", userId);
    }

    /**
     * Finds a group by id.
     *
     * @param id the group id
     * @return the group, or empty if none has that id
     */
    public Optional<RepositoryGroup> findById(String id) {
        return datasource.findById(id);
    }

    /**
     * Stores a new group.
     *
     * @param group the group, with its id set
     */
    public void create(RepositoryGroup group) {
        datasource.create(group);
    }

    /**
     * Deletes a group; its memberships go with it (cascade), the tracked
     * repositories stay.
     *
     * @param id the group id
     */
    public void deleteById(String id) {
        datasource.deleteById(id);
    }

}
