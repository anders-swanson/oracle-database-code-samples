package com.example.ucp;

import java.util.Properties;

public record LabeledConnectionReport(
        int queryResult,
        int transactionIsolation,
        Properties labels,
        Properties unmatchedLabels
) {
}
