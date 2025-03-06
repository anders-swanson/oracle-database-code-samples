package com.example.news.events.parser;

import lombok.ToString;

@ToString
public class InvalidNewsException extends Exception {
    private final String content;

    public InvalidNewsException(String content) {
        this.content = content;
    }
}
