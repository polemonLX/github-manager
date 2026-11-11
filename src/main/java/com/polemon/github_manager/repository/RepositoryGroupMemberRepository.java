package com.polemon.github_manager.repository;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.RepositoryGroupMember;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stored {@link RepositoryGroupMember memberships} of tracked repositories in
 * groups.
 */
@Repository
public class RepositoryGroupMemberRepository {

    /**
     * The datasource holding the memberships.
     */
    private final IDatasource<RepositoryGroupMember> datasource;

    public RepositoryGroupMemberRepository(IDatasource<RepositoryGroupMember> datasource) {
        this.datasource = datasource;
    }

    /**
     * Lists the repositories in a group.
     *
     * @param groupId the group id
     * @return the group's memberships; empty if the group is empty
     */
    public List<RepositoryGroupMember> findByGroup(String groupId) {
        return datasource.findAll("groupId", groupId);
    }

    /**
     * Lists the groups a tracked repository is in.
     *
     * @param trackedRepositoryId the id of the user's link to the repository
     * @return its memberships; empty if it is in no group
     */
    public List<RepositoryGroupMember> findByTrackedRepository(String trackedRepositoryId) {
        return datasource.findAll("trackedRepositoryId", trackedRepositoryId);
    }

    /**
     * Stores a new membership.
     *
     * @param member the membership, with its id set
     */
    public void create(RepositoryGroupMember member) {
        datasource.create(member);
    }

    /**
     * Deletes a membership.
     *
     * @param id the membership id
     */
    public void deleteById(String id) {
        datasource.deleteById(id);
    }

}
