package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.RepositoryGroupMember;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryGroupMemberRepositoryTest {

    @SuppressWarnings("unchecked")
    private final IDatasource<RepositoryGroupMember> datasource = mock(IDatasource.class);
    private final RepositoryGroupMemberRepository repository = new RepositoryGroupMemberRepository(datasource);

    @Test
    void findsByGroupAndByTrackedRepository() {
        List<RepositoryGroupMember> byGroup = List.of(new RepositoryGroupMember("m1", "g1", "l1"));
        List<RepositoryGroupMember> byLink = List.of(new RepositoryGroupMember("m2", "g2", "l2"));
        when(datasource.findAll("groupId", "g1")).thenReturn(byGroup);
        when(datasource.findAll("trackedRepositoryId", "l2")).thenReturn(byLink);

        assertEquals(byGroup, repository.findByGroup("g1"));
        assertEquals(byLink, repository.findByTrackedRepository("l2"));
    }

    @Test
    void createAndDeleteDelegate() {
        RepositoryGroupMember member = new RepositoryGroupMember("m1", "g1", "l1");

        repository.create(member);
        repository.deleteById("m1");

        verify(datasource).create(member);
        verify(datasource).deleteById("m1");
    }

}
