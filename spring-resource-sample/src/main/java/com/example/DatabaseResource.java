package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.ResultSet;

import org.springframework.core.io.AbstractResource;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseResource extends AbstractResource {
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseLocation location;

    public DatabaseResource(JdbcTemplate jdbcTemplate, DatabaseLocation location) {
        this.jdbcTemplate = jdbcTemplate;
        this.location = location;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return jdbcTemplate.query(location.query(), rs -> {
           if (rs.next()) {
               Blob blob = rs.getBlob(location.getBlobColumn());
               return blob.getBinaryStream();
           }
           return null;
        }, location.getFileName());
    }

    @Override
    public long contentLength() throws IOException {
        InputStream is = getInputStream();
        if (is == null) {
            return 0;
        }
        try {
            long size = 0L;
            int read;
            for(byte[] buf = new byte[256];
                (read = is.read(buf)) != -1;
                size += read) {}
            return size;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean exists() {
        return Boolean.TRUE.equals(jdbcTemplate.query(location.query(), ResultSet::next, location.getFileName()));
    }

    @Override
    public String getDescription() {
        return location.description();
    }
}
