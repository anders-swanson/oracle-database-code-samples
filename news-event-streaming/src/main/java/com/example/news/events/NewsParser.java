package com.example.news.events;

import com.example.news.events.model.News;
import org.springframework.stereotype.Component;

@Component
public class NewsParser {
    public News parse(String raw) {
        return new News();
    }
}
