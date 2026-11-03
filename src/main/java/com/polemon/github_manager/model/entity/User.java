package com.polemon.github_manager.model.entity;

import com.polemon.api_lib.persistence.annotation.Store;
import com.polemon.api_lib.persistence.model.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user of this app, created the first time they store something. The id is
 * auth's user id (the identity token's subject), so each user only ever sees
 * their own token, repositories and workflows.
 */
@Getter
@Setter
@NoArgsConstructor
@Store("users")
public class User extends Entity {

    /**
     * The user's GitHub personal access token, or {@code null} if none is set.
     */
    private String githubToken;

    public User(String id, String githubToken) {
        super(id);
        this.githubToken = githubToken;
    }

}
