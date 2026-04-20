package com.example.courses;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CoursesDatabaseInfoService {
    private final JdbcTemplate jdbcTemplate;

    public CoursesDatabaseInfoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DatabaseInfoResponse getDatabaseInfo() {
        String schema = jdbcTemplate.queryForObject(
                "select sys_context('USERENV', 'CURRENT_SCHEMA') from dual",
                String.class
        );
        String container = jdbcTemplate.queryForObject(
                "select sys_context('USERENV', 'CON_NAME') from dual",
                String.class
        );
        Long rowCount = jdbcTemplate.queryForObject(
                """
                select
                    (select count(*) from course_catalog) +
                    (select count(*) from course_prerequisites) +
                    (select count(*) from course_offerings)
                from dual
                """,
                Long.class
        );
        return new DatabaseInfoResponse("courses", schema, container, rowCount == null ? 0L : rowCount);
    }

    public record DatabaseInfoResponse(String application, String schema, String container, long rowCount) {
    }
}
