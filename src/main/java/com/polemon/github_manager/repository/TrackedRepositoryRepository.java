package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.TrackedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stored {@link TrackedRepository links} between users and the repositories
 * they track.
 */
@Repository
public class TrackedRepositoryRepository {

    /**
     * The datasource holding the links.
     */
    private final IDatasource<TrackedRepository> datasource;

    public TrackedRepositoryRepository(IDatasource<TrackedRepository> datasource) {
        this.datasource = datasource;
    }

    /**
     * Lists the repositories a user tracks.
     *
     * @param userId the user id
     * @return the user's links; empty if there are none
     */
    public List<TrackedRepository> findByUser(String userId) {
        return datasource.findAll("userId", userId);
    }

    /**
     * Lists the users who track a repository.
     *
     * @param repositoryId the repository id
     * @return the repository's links; empty if nobody tracks it
     */
    public List<TrackedRepository> findByRepository(String repositoryId) {
        return datasource.findAll("repositoryId", repositoryId);
    }

    /**
     * Stores a new link.
     *
     * @param trackedRepository the link, with its id set
     */
    public void create(TrackedRepository trackedRepository) {
        datasource.create(trackedRepository);
    }

    /**
     * Deletes a link.
     *
     * @param id the id of the link
     */
    public void deleteById(String id) {
        datasource.deleteById(id);
    }

}
