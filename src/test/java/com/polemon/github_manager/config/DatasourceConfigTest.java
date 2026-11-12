package com.polemon.github_manager.config;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasourceConfigTest {

    @Test
    void exposesADatasourcePerEntity() throws Exception {
        // A stand-in JDBC driver for jdbc:fake: urls, so the connection pool starts without a database.
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        Driver driver = mock(Driver.class);
        when(driver.acceptsURL(anyString())).thenAnswer(call -> call.<String>getArgument(0).startsWith("jdbc:fake:"));
        when(driver.connect(anyString(), any())).thenReturn(connection);
        DriverManager.registerDriver(driver);
        try {
            DatasourceConfig config = new DatasourceConfig("jdbc:fake:github_manager", "user", "pass");

            assertNotNull(config.userDatasource());
            assertNotNull(config.repositoryDatasource());
            assertNotNull(config.trackedRepositoryDatasource());
            assertNotNull(config.repositoryGroupDatasource());
            assertNotNull(config.repositoryGroupMemberDatasource());
            assertNotNull(config.workflowDatasource());
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

}
