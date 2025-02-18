package com.example.splitter;

import java.util.List;

public class LineSplitter implements Splitter<String> {

    @Override
    public List<String> split(String s) {
        return List.of(s.split("\\r?\\n|\\r"));
    }
}
