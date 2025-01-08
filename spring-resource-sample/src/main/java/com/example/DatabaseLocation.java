package com.example;

public class DatabaseLocation {
    static final String PROTOCOL_PREFIX = "oracledatabase://";

    private final String table;
    private final String blobColumn;
    private final String fileNameColumn;
    private final String fileName;

    public DatabaseLocation(String table, String blobColumn, String fileNameColumn, String fileName) {
        this.table = table;
        this.blobColumn = blobColumn;
        this.fileNameColumn = fileNameColumn;
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getBlobColumn() {
        return blobColumn;
    }

    public String query() {
        return """
                select %s from %s where %s = ?
                """.formatted(blobColumn, table, fileNameColumn);
    }

    public String description() {
        return "%s%s/%s".formatted(PROTOCOL_PREFIX, table, fileName);
    }
}