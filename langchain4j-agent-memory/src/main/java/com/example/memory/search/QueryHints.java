package com.example.memory.search;

import java.util.List;

public record QueryHints(
        String service,
        String environment,
        String incidentId,
        String changeTicket,
        List<String> keywords
) {
}
