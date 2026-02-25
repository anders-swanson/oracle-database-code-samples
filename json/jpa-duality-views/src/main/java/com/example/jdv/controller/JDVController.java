package com.example.jdv.controller;

import com.example.jdv.movie.Actor;
import com.example.jdv.movie.Movie;
import com.oracle.spring.json.duality.annotation.JsonRelationalDualityView;
import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.jsonb.JSONBRowMapper;
import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static com.oracle.spring.json.duality.builder.Annotations.getViewName;

@RestController
public class JDVController {
    private final JSONB jsonb;
    private final JdbcClient jdbcClient;
    private final DataSource dataSource;

    public JDVController(JSONB jsonb, JdbcClient jdbcClient, DataSource dataSource) {
        this.jsonb = jsonb;
        this.jdbcClient = jdbcClient;
        this.dataSource = dataSource;
    }


    @PostMapping("/actor")
    public Actor createActor(Actor actor) {
        long id = save(actor, Actor.class);
        return findById(Actor.class, id).orElse(null);
    }

    @PostMapping("/movie)")
    public Movie createMovie(Movie movie) {
        long id = save(movie, Movie.class);
        return findById(Movie.class, id).orElse(null);
    }

    public <T> long save(T entity, Class<T> entityJavaType) {
        String viewName = getViewName(entityJavaType, entityJavaType.getAnnotation(JsonRelationalDualityView.class));
        final String sql = """
                insert into %s (data) values (?)
                returning json_value(data, '$._id' returning number) into ?
                """.formatted(viewName);

        byte[] oson = jsonb.toOSON(entity);
        try (Connection conn = dataSource.getConnection();
             OraclePreparedStatement ps = (OraclePreparedStatement) conn.prepareStatement(sql)) {
            ps.setObject(1, oson, OracleTypes.JSON);
            // Register the RETURNING bind (2nd bind)
            ps.registerReturnParameter(2, OracleTypes.NUMBER);
            ps.executeUpdate();

            // Get returned JSON document
            try (ResultSet rs = ps.getReturnResultSet()) {
                if (rs.next()) {
                    return rs.getLong(1);
                } else {
                    throw new SQLException("Insert failed: no returned document obtained.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> Optional<T> findById(Class<T> entityJavaType, Long id) {
        String viewName = getViewName(entityJavaType, entityJavaType.getAnnotation(JsonRelationalDualityView.class));
        final String sql = """
                select * from %s dv
            where dv.data."_id" = ?
            """.formatted(viewName);

        JSONBRowMapper<T> rowMapper = new JSONBRowMapper<>(jsonb, entityJavaType);
        return jdbcClient.sql(sql)
                .param(id)
                .query(rowMapper)
                .optional();
    }
}
