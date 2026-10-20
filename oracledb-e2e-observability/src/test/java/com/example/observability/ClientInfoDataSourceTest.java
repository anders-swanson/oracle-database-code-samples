package com.example.observability;

import java.sql.Connection;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientInfoDataSourceTest {
    @Test
    void closesConnectionIfDecorationFailsIncludingCredentialedBorrow() throws Exception {
        DataSource pool = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(pool.getConnection()).thenReturn(connection);
        when(pool.getConnection("user", "password")).thenReturn(connection);
        var failure = new SQLClientInfoException("Cannot set metadata", Map.of());
        doThrow(failure).when(connection).setClientInfo(any(Properties.class));
        doThrow(new SQLException("Cannot close")).when(connection).close();
        var decorated = new ClientInfoDataSource(pool, false);
        assertThatThrownBy(decorated::getConnection).isSameAs(failure)
                .hasSuppressedException(new SQLException("Cannot close"));
        assertThatThrownBy(() -> decorated.getConnection("user", "password")).isSameAs(failure);
        verify(pool).getConnection("user", "password");
        verify(connection, org.mockito.Mockito.times(2)).close();
    }
}
