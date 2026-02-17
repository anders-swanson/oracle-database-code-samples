package com.example.jdv.controller;

import com.example.jdv.movie.Actor;
import com.example.jdv.movie.Movie;
import com.oracle.spring.json.duality.annotation.JsonRelationalDualityView;
import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.jsonb.JSONBRowMapper;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

import static com.oracle.spring.json.duality.builder.Annotations.getViewName;

@RestController
public class JDVController {
    private final JSONB jsonb;
    private final JdbcClient jdbcClient;

    public JDVController(JSONB jsonb, JdbcClient jdbcClient) {
        this.jsonb = jsonb;
        this.jdbcClient = jdbcClient;
    }


    @PostMapping("/actor")
    public Actor createActor(Actor actor) {
        save(actor, Actor.class);
        return findById(Actor.class, actor.getActorId()).orElse(null);
    }

    @PostMapping("/movie)")
    public Movie createMovie(Movie movie) {
        save(movie, Movie.class);
        return findById(Movie.class, movie.getMovieId()).orElse(null);
    }

    public <T> int save(T entity, Class<T> entityJavaType) {
        String viewName = getViewName(entityJavaType, entityJavaType.getAnnotation(JsonRelationalDualityView.class));
        final String sql = """
                insert into %s (data) values (?)
                """.formatted(viewName);

        byte[] oson = jsonb.toOSON(entity);
        return jdbcClient.sql(sql)
                .param(1, oson, OracleTypes.JSON)
                .update();
    }

    public <T> Optional<T> findById(Class<T> entityJavaType, String id) {
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

    private byte[] uuidToRaw16(UUID uuid) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }
}
