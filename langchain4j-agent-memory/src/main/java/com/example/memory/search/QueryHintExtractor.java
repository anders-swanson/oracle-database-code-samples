package com.example.memory.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueryHintExtractor {
    private static final Pattern INCIDENT_PATTERN = Pattern.compile("\\bINC\\d{4,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANGE_PATTERN = Pattern.compile("\\bCHG\\d{4,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of(
            "what", "which", "should", "could", "would", "after", "before", "about", "during",
            "from", "that", "with", "have", "this", "there", "their", "into", "remember", "draft"
    );
    private static final Set<String> KNOWN_SERVICES = Set.of("checkout", "payments", "inventory", "identity", "platform");
    private static final Set<String> KNOWN_ENVIRONMENTS = Set.of("prod", "stage");

    public static QueryHints extract(String question) {
        String incidentId = firstMatch(INCIDENT_PATTERN, question);
        String changeTicket = firstMatch(CHANGE_PATTERN, question);
        Set<String> keywords = new LinkedHashSet<>();
        String service = null;
        String environment = null;

        for (String token : question.toLowerCase(Locale.US).split("[^a-z0-9]+")) {
            if (token.isBlank() || isIgnoredToken(token)) {
                continue;
            }
            if (KNOWN_SERVICES.contains(token)) {
                service = token;
            }
            if (KNOWN_ENVIRONMENTS.contains(token)) {
                environment = token;
            }
            keywords.add(token);
        }
        if (incidentId != null) {
            keywords.add(incidentId);
        }
        if (changeTicket != null) {
            keywords.add(changeTicket);
        }

        return new QueryHints(
                service,
                environment,
                incidentId,
                changeTicket,
                new ArrayList<>(keywords)
        );
    }

    private static boolean isIgnoredToken(String token) {
        if (STOP_WORDS.contains(token)) {
            return true;
        }
        return token.length() < 3 && !isNumericToken(token);
    }

    private static boolean isNumericToken(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return !token.isEmpty();
    }

    private static String firstMatch(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group().toUpperCase(Locale.US);
        }
        return null;
    }
}
