package com.example.news;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.example.news.model.News;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.example.news.Utils.readFile;

public class Test {
//    @org.junit.jupiter.api.Test
//    void t() throws IOException {
//        String data = readFile("test-data.json");
//
//        String[] split = data.split("\n");
//
//        List<String> output = new ArrayList<>();
//
//        ObjectMapper mapper = new ObjectMapper();
//
//        for (String s : split) {
//            News newsItem = mapper.readValue(s, News.class);
//            if (newsItem.getText().endsWith(".")) {
//                output.add(newsItem.getText() + newsItem.getHighlights());
//            } else {
//                output.add(newsItem.getText() + "." + newsItem.getHighlights());
//            }
//        }
//        byte[] bytes = mapper.writeValueAsBytes(output);
//        Files.write(Paths.get("input-data.json"), bytes);
//    }

    @org.junit.jupiter.api.Test
    void t2() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<String> out = new ArrayList<>();
        String data = readFile("input-data.json");
        List<String> strings = mapper.readValue(data, new TypeReference<List<String>>() {
        });
        strings.stream().findFirst().ifPresent(s -> {
            out.add(s);
            try {
                Files.write(Paths.get("one-record"), mapper.writeValueAsBytes(out));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
