package com.example.nl2sql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

@Component
public class SelectAIService {
    private final DataSource dataSource;
    private final String profile;

    public SelectAIService(DataSource dataSource,
                           @Value("selectai.profile") String profile) {
        this.dataSource = dataSource;
        this.profile = profile;
    }

    public String showSQL(String prompt) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                BEGIN
                    ? := DBMS_CLOUD_AI.GENERATE(
                             prompt       => ?,
                             action       => 'showsql',
                             profile_name => ?);
                END;
                """;

            try (CallableStatement statement = conn.prepareCall(sql)) {
                statement.registerOutParameter(1, Types.CLOB);
                statement.setString(2, prompt);
                statement.setString(3, profile);
                statement.execute();
                return statement.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
