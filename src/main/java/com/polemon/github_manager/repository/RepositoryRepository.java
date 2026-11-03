package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.RepositoryEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Stored {@link RepositoryEntity GitHub repositories}, shared by every user who
 * tracks them.
 */
@Repository
public class RepositoryRepository {

    /**
     * The datasource holding the repositories.
     */
    private final IDatasource<RepositoryEntity> datasource;

    public RepositoryRepository(IDatasource<RepositoryEntity> datasource) {
        this.datasource = datasource;
    }

    /**
     * Finds a repository by id.
     *
     * @param id the repository id
     * @return the repository, or empty if none has that id
     */
    public Optional<RepositoryEntity> findById(String id) {
        return datasource.findById(id);
    }

    /**
     * Finds a repository by its GitHub owner and name.
     *
     * @param owner the repository owner
     * @param name  the repository name
     * @return the repository, or empty if nobody tracks it
     */
    public Optional<RepositoryEntity> findByOwnerAndName(String owner, String name) {
        return datasource.findAll("owner", owner).stream()
                .filter(repository -> repository.getName().equals(name))
                .findFirst();
    }

    /**
     * Lists every stored repository.
     *
     * @return all repositories; empty if there are none
     */
    public List<RepositoryEntity> getAll() {
        return datasource.getAll();
    }

    /**
     * Stores a new repository.
     *
     * @param repository the repository, with its id set
     */
    public void create(RepositoryEntity repository) {
        datasource.create(repository);
    }

    /**
     * Replaces a stored repository.
     *
     * @param repository the repository, with the id of the entry to replace
     */
    public void update(RepositoryEntity repository) {
        datasource.update(repository);
    }

    /**
     * Deletes a repository; the links of the users who tracked it go with it.
     *
     * @param id the repository id
     */
    public void deleteById(String id) {
        datasource.deleteById(id);
    }

}
