package com.example.configserver;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;


/**
 * [Optional] CRUD API to manage properties in the database schema.
 * Properties in the database may be managed independently of clients.
 */
@RestController
@RequestMapping("/api/properties")
public class PropertiesController {
    public record Property(
            Long id,
            String application,
            String profile,
            String label,
            String propKey,
            String value
    ) {}


    private final JdbcClient jdbc;

    public PropertiesController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- GET ALL ----------
    @GetMapping
    public List<Property> getAll() {
        return jdbc.sql("""
            SELECT id, application, profile, label, prop_key, value
            FROM PROPERTIES
            """)
                .query(Property.class)
                .list();
    }

    // ---------- GET ONE ----------
    @GetMapping("/{id}")
    public ResponseEntity<Property> getById(@PathVariable Long id) {
        return jdbc.sql("""
            SELECT id, application, profile, label, prop_key, value
            FROM PROPERTIES WHERE id = :id
            """)
                .param("id", id)
                .query(Property.class)
                .optional()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------- CREATE ----------
    @PostMapping
    public ResponseEntity<Property> create(@RequestBody Property p) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int id = jdbc.sql("""
            INSERT INTO PROPERTIES(application, profile, label, prop_key, value)
            VALUES (:application, :profile, :label, :propKey, :value)
            """)
                .param("application", p.application())
                .param("profile", p.profile())
                .param("label", p.label())
                .param("propKey", p.propKey())
                .param("value", p.value())
                .update(keyHolder, "id");

        Property created = jdbc.sql("""
                        SELECT id, application, profile, label, prop_key, value
                        FROM PROPERTIES WHERE id = :id
                        """)
                .param("id", id)
                .query(Property.class)
                .single();
        return ResponseEntity
                .created(URI.create("/api/properties/" + id))
                .body(created);
    }

    // ---------- UPDATE ----------
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody Property p) {
        int rows = jdbc.sql("""
            UPDATE PROPERTIES
               SET application = :application,
                   profile = :profile,
                   label = :label,
                   prop_key = :propKey,
                   value = :value
             WHERE id = :id
            """)
                .param("application", p.application())
                .param("profile", p.profile())
                .param("label", p.label())
                .param("propKey", p.propKey())
                .param("value", p.value())
                .param("id", id)
                .update();

        return rows == 0 ? ResponseEntity.notFound().build() : ResponseEntity.noContent().build();
    }

    // ---------- DELETE ----------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        int rows = jdbc.sql("DELETE FROM PROPERTIES WHERE id = :id")
                .param("id", id)
                .update();

        return rows == 0 ? ResponseEntity.notFound().build() : ResponseEntity.noContent().build();
    }
}
