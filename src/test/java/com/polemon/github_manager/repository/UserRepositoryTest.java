package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRepositoryTest {

    @SuppressWarnings("unchecked")
    private final IDatasource<User> datasource = mock(IDatasource.class);
    private final UserRepository repository = new UserRepository(datasource);

    @Test
    void findByIdDelegates() {
        User user = new User("u1", "token");
        when(datasource.findById("u1")).thenReturn(Optional.of(user));

        assertSame(user, repository.findById("u1").orElseThrow());
    }

    @Test
    void getAllDelegates() {
        List<User> users = List.of(new User("u1", null), new User("u2", "token"));
        when(datasource.getAll()).thenReturn(users);

        assertEquals(users, repository.getAll());
    }

    @Test
    void createAndUpdateDelegate() {
        User user = new User("u1", "token");

        repository.create(user);
        repository.update(user);

        verify(datasource).create(user);
        verify(datasource).update(user);
    }

}
