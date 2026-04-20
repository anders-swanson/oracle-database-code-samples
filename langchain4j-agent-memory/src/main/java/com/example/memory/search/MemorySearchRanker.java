package com.example.memory.search;

import com.example.memory.model.MemoryHit;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MemorySearchRanker {
    private static final int FUSION_K = 60;
    private static final double REFERENCE_MATCH_BONUS = 0.03d;
    private static final double WORD_MATCH_BONUS = 0.003d;
    private static final double NUMBER_MATCH_BONUS = 0.008d;
    private static final double MAX_KEYWORD_BONUS = 0.02d;

    List<MemoryHit> fuse(List<MemoryHit> vectorHits, List<MemoryHit> textHits, QueryHints hints, int maxResults) {
        Map<Long, CombinedHit> combined = new LinkedHashMap<>();
        for (int i = 0; i < vectorHits.size(); i++) {
            MemoryHit hit = vectorHits.get(i);
            CombinedHit current = combined.computeIfAbsent(hit.id(), ignored -> new CombinedHit(hit));
            current.vectorScore = hit.vectorScore();
            current.vectorRank = i + 1;
        }
        for (int i = 0; i < textHits.size(); i++) {
            MemoryHit hit = textHits.get(i);
            CombinedHit current = combined.computeIfAbsent(hit.id(), ignored -> new CombinedHit(hit));
            current.textScore = hit.textScore();
            current.textRank = i + 1;
        }

        return combined.values().stream()
                .map(hit -> hit.toMemoryHit(hints))
                .sorted(Comparator.comparingDouble(MemoryHit::fusedScore).reversed().thenComparing(MemoryHit::id))
                .limit(maxResults)
                .toList();
    }

    private static final class CombinedHit {
        private final MemoryHit base;
        private int vectorRank;
        private int textRank;
        private double vectorScore;
        private int textScore;

        private CombinedHit(MemoryHit base) {
            this.base = base;
        }

        private MemoryHit toMemoryHit(QueryHints hints) {
            double fused = reciprocalRank(vectorRank) + reciprocalRank(textRank);
            if (matchesReference(hints)) {
                fused += REFERENCE_MATCH_BONUS;
            }
            fused += keywordOverlapBonus(hints);
            return new MemoryHit(
                    base.id(),
                    base.memoryKind(),
                    base.title(),
                    base.summary(),
                    base.searchText(),
                    base.service(),
                    base.environment(),
                    base.incidentId(),
                    base.changeTicket(),
                    vectorScore,
                    textScore,
                    fused,
                    matchedBy()
            );
        }

        private double reciprocalRank(int rank) {
            return rank == 0 ? 0.0d : 1.0d / (FUSION_K + rank);
        }

        private double keywordOverlapBonus(QueryHints hints) {
            Set<String> indexedTerms = tokenize(base.searchText());
            double bonus = 0.0d;
            for (String keyword : hints.keywords()) {
                String normalized = keyword.toLowerCase(Locale.US);
                if (!indexedTerms.contains(normalized)) {
                    continue;
                }
                bonus += isNumericToken(normalized) ? NUMBER_MATCH_BONUS : WORD_MATCH_BONUS;
            }
            return Math.min(bonus, MAX_KEYWORD_BONUS);
        }

        private boolean matchesReference(QueryHints hints) {
            return equalsIgnoreCase(base.incidentId(), hints.incidentId())
                    || equalsIgnoreCase(base.changeTicket(), hints.changeTicket());
        }

        private String matchedBy() {
            if (vectorRank > 0 && textRank > 0) {
                return "BOTH";
            }
            if (vectorRank > 0) {
                return "VECTOR";
            }
            return "TEXT";
        }

        private boolean equalsIgnoreCase(String left, String right) {
            return left != null && right != null && left.equalsIgnoreCase(right);
        }

        private Set<String> tokenize(String value) {
            Set<String> tokens = new HashSet<>();
            for (String token : value.toLowerCase(Locale.US).split("[^a-z0-9]+")) {
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
            return tokens;
        }

        private boolean isNumericToken(String token) {
            for (int i = 0; i < token.length(); i++) {
                if (!Character.isDigit(token.charAt(i))) {
                    return false;
                }
            }
            return !token.isEmpty();
        }
    }
}
