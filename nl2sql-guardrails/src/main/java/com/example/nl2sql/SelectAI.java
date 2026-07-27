package com.example.nl2sql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

@Component
public class SelectAI {
    private final DataSource dataSource;
    private final String profile;

    public SelectAI(DataSource dataSource,
                    @Value("selectai.profile") String profile) {
        this.dataSource = dataSource;
        this.profile = profile;
    }

    public String run(String prompt, Action action) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                BEGIN
                    ? := DBMS_CLOUD_AI.GENERATE(
                             prompt       => ?,
                             action       => ?,
                             profile_name => ?);
                END;
                """;

            try (CallableStatement statement = conn.prepareCall(sql)) {
                statement.registerOutParameter(1, Types.CLOB);
                statement.setString(2, prompt);
                statement.setString(3, action.getAction());
                statement.setString(4, "MY_PROFILE");
                statement.execute();
                return statement.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // runsql, showsql, explainsql, narrate, or chat
    public enum Action {
        RUNSQL("RUNSQL"),
        SHOWSQL("SHOWSQL"),
        EXPLAINSQL("EXPLAINSQL"),
        NARRATE("NARRATE"),
        CHAT("CHAT");

        private final String action;

        Action(String action) {
            this.action = action;
        }

        public String getAction() {
            return action;
        }

    }
}
