package com.polemon.github_manager.config;

import com.polemon.api_lib.persistence.IDatasource;
import com.polemon.github_manager.model.entity.InternalWorkflowEntity;
import com.polemon.github_manager.model.entity.RepositoryEntity;
import com.polemon.github_manager.model.entity.TrackedRepository;
import com.polemon.github_manager.model.entity.User;
import com.polemon.postgresql.DatasourceManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the PostgreSQL datasources of every entity; the only place that uses
 * {@code postgresql-lib}. Each datasource creates its table, unique indexes and
 * foreign keys when built.
 */
@Configuration
public class DatasourceConfig {

    /**
     * JDBC url of the github-manager database.
     */
    private final String url;

    /**
     * Database username.
     */
    private final String username;

    /**
     * Database password.
     */
    private final String password;

    public DatasourceConfig(@Value("${github-manager.datasource.url}") String url,
                            @Value("${github-manager.datasource.username}") String username,
                            @Value("${github-manager.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /**
     * The users datasource.
     *
     * @return the datasource of {@link User}
     */
    @Bean
    public IDatasource<User> userDatasource() {
        return DatasourceManager.getInstance().getDatasource(url, username, password, User.class);
    }

    /**
     * The repositories datasource.
     *
     * @return the datasource of {@link RepositoryEntity}
     */
    @Bean
    public IDatasource<RepositoryEntity> repositoryDatasource() {
        return DatasourceManager.getInstance().getDatasource(url, username, password, RepositoryEntity.class);
    }

    /**
     * The datasource of the links between users and the repositories they track.
     *
     * @return the datasource of {@link TrackedRepository}
     */
    @Bean
    public IDatasource<TrackedRepository> trackedRepositoryDatasource() {
        return DatasourceManager.getInstance().getDatasource(url, username, password, TrackedRepository.class);
    }

    /**
     * The workflows datasource.
     *
     * @return the datasource of {@link InternalWorkflowEntity}
     */
    @Bean
    public IDatasource<InternalWorkflowEntity> workflowDatasource() {
        return DatasourceManager.getInstance().getDatasource(url, username, password, InternalWorkflowEntity.class);
    }

}
