package com.example.okafkamemory.retrieval;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Whole Unicode words, counted once regardless of repetitions or case. */
public final class MemorySearchTerms {
    public static final int MAX_QUERY_LENGTH = 1024;
    public static final int MAX_QUERY_TERMS = 32;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private MemorySearchTerms() {
    }

    public static Set<String> queryTerms(String query) {
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query must contain at most " + MAX_QUERY_LENGTH + " characters");
        }
        Set<String> terms = words(query);
        if (terms.isEmpty() || terms.size() > MAX_QUERY_TERMS) {
            throw new IllegalArgumentException("query must contain between 1 and " + MAX_QUERY_TERMS + " distinct terms");
        }
        return terms;
    }

    public static double score(String text, Set<String> queryTerms) {
        Set<String> memoryTerms = words(text);
        return queryTerms.stream().filter(memoryTerms::contains).count() / (double) queryTerms.size();
    }

    private static Set<String> words(String text) {
        Set<String> terms = new LinkedHashSet<>();
        WORD.matcher(text.toLowerCase(Locale.ROOT)).results().forEach(match -> terms.add(match.group()));
        return terms;
    }
}
