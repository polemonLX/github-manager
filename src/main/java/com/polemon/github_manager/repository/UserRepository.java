package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Stored {@link User users}.
 */
@Repository
public class UserRepository {

    /**
     * The datasource holding the users.
     */
    private final IDatasource<User> datasource;

    public UserRepository(IDatasource<User> datasource) {
        this.datasource = datasource;
    }

    /**
     * Finds a user by id.
     *
     * @param id the user id (auth's)
     * @return the user, or empty if they never stored anything
     */
    public Optional<User> findById(String id) {
        return datasource.findById(id);
    }

    /**
     * Lists every user.
     *
     * @return all users; empty if there are none
     */
    public List<User> getAll() {
        return datasource.getAll();
    }

    /**
     * Stores a new user.
     *
     * @param user the user, with its id set
     */
    public void create(User user) {
        datasource.create(user);
    }

    /**
     * Replaces a stored user.
     *
     * @param user the user, with the id of the entry to replace
     */
    public void update(User user) {
        datasource.update(user);
    }

}
