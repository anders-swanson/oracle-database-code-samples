package com.example.graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * Generates a simple SVG diagram for the sample property graph.
 */
public final class PropertyGraphDiagramGenerator {
    private static final int WIDTH = 1120;
    private static final int HEIGHT = 760;

    private PropertyGraphDiagramGenerator() {
    }

    /**
     * Writes an SVG diagram to disk using the sample's people, friendship edges, and query results.
     */
    public static void writeSvg(JdbcPropertyGraphSample sample, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        Files.writeString(outputFile, buildSvg(sample));
    }

    /**
     * Builds an SVG string that renders the teaching graph and the sample query results.
     */
    public static String buildSvg(JdbcPropertyGraphSample sample) {
        List<JdbcPropertyGraphSample.Person> people = sample.listPeople();
        List<JdbcPropertyGraphSample.Friendship> friendships = sample.listFriendships();
        Map<Integer, NodePosition> positions = Map.of(
                1, new NodePosition(350, 210),
                2, new NodePosition(640, 115),
                3, new NodePosition(640, 365),
                4, new NodePosition(940, 160),
                5, new NodePosition(940, 430)
        );
        Map<Integer, JdbcPropertyGraphSample.Person> peopleById = people.stream()
                .collect(Collectors.toMap(JdbcPropertyGraphSample.Person::personId, person -> person));

        StringBuilder svg = new StringBuilder();
        svg.append("""
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                  <defs>
                    <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
                      <path d="M 0 0 L 10 5 L 0 10 z" fill="#64748b"/>
                    </marker>
                  </defs>
                  <style>
                    text { font-family: "Helvetica Neue", Helvetica, Arial, sans-serif; fill: #111827; }
                    .title { font-size: 28px; font-weight: 700; }
                    .subtitle { font-size: 14px; fill: #475569; }
                    .edge { stroke: #94a3b8; stroke-width: 2.5; marker-end: url(#arrow); }
                    .edge-label-box { fill: rgba(255,255,255,0.94); stroke: #cbd5e1; stroke-width: 1; rx: 8; ry: 8; }
                    .edge-label { font-size: 12px; font-weight: 600; fill: #334155; }
                    .person { fill: #0f766e; stroke: #ffffff; stroke-width: 3; }
                    .person-box { fill: rgba(248,250,252,0.95); stroke: #cbd5e1; stroke-width: 1; rx: 12; ry: 12; }
                    .person-name { font-size: 16px; font-weight: 700; }
                    .person-detail { font-size: 12px; fill: #475569; }
                    .results-box { fill: #f8fafc; stroke: #cbd5e1; stroke-width: 1; rx: 14; ry: 14; }
                    .results-header { font-size: 14px; font-weight: 700; fill: #1e3a8a; }
                    .results-row { font-size: 13px; fill: #1f2937; }
                    .legend { font-size: 13px; fill: #475569; }
                  </style>
                  <rect x="0" y="0" width="%d" height="%d" fill="#f8fafc"/>
                  <text x="50" y="52" class="title">Oracle Property Graph Sample Diagram</text>
                  <text x="50" y="77" class="subtitle">People are vertices, friendships are directed edges, and the side panel shows the sample GRAPH_TABLE results.</text>
                """.formatted(WIDTH, HEIGHT, WIDTH, HEIGHT, WIDTH, HEIGHT));

        for (JdbcPropertyGraphSample.Person person : people) {
            svg.append(personSvg(person, positions.get(person.personId())));
        }

        for (JdbcPropertyGraphSample.Friendship friendship : friendships) {
            svg.append(edgePathSvg(friendship, positions));
        }

        for (JdbcPropertyGraphSample.Friendship friendship : uniqueFriendships(friendships).values()) {
            svg.append(edgeLabelSvg(friendship, peopleById, positions));
        }

        svg.append(resultsPanelSvg(sample));
        svg.append("""
                  <text x="50" y="715" class="legend">Arrow direction follows the source and destination keys defined in CREATE PROPERTY GRAPH.</text>
                  <text x="620" y="715" class="legend">Reciprocal rows make each friendship behave like an undirected social connection.</text>
                </svg>
                """);
        return svg.toString();
    }

    private static String personSvg(JdbcPropertyGraphSample.Person person, NodePosition position) {
        return """
                  <circle cx="%.2f" cy="%.2f" r="18" class="person"/>
                  <rect x="%.2f" y="%.2f" width="124" height="52" class="person-box"/>
                  <text x="%.2f" y="%.2f" class="person-name">%s</text>
                  <text x="%.2f" y="%.2f" class="person-detail">%s</text>
                """.formatted(
                position.x(),
                position.y(),
                position.x() - 62,
                position.y() + 28,
                position.x() - 48,
                position.y() + 48,
                escape(person.name()),
                position.x() - 48,
                position.y() + 66,
                escape(person.hometown())
        );
    }

    private static String edgePathSvg(
            JdbcPropertyGraphSample.Friendship friendship,
            Map<Integer, NodePosition> positions
    ) {
        NodePosition source = positions.get(friendship.person1Id());
        NodePosition destination = positions.get(friendship.person2Id());
        EdgeGeometry geometry = edgeGeometry(friendship, source, destination);
        return """
                  <path d="M %.2f %.2f Q %.2f %.2f %.2f %.2f" class="edge" fill="none"/>
                """.formatted(
                geometry.startX(), geometry.startY(),
                geometry.controlX(), geometry.controlY(),
                geometry.endX(), geometry.endY()
        );
    }

    private static String edgeLabelSvg(
            JdbcPropertyGraphSample.Friendship friendship,
            Map<Integer, JdbcPropertyGraphSample.Person> peopleById,
            Map<Integer, NodePosition> positions
    ) {
        NodePosition source = positions.get(friendship.person1Id());
        NodePosition destination = positions.get(friendship.person2Id());
        EdgeGeometry geometry = edgeGeometry(friendship, source, destination);
        JdbcPropertyGraphSample.Person sourcePerson = peopleById.get(friendship.person1Id());
        JdbcPropertyGraphSample.Person destinationPerson = peopleById.get(friendship.person2Id());
        String label = "%s : %s".formatted(sourcePerson.name(), destinationPerson.name());
        String detail = "since %d | strength %d".formatted(friendship.sinceYear(), friendship.strength());
        double labelWidth = Math.max(160, label.length() * 8.2);
        return """
                  <rect x="%.2f" y="%.2f" width="%.2f" height="34" class="edge-label-box"/>
                  <text x="%.2f" y="%.2f" class="edge-label">%s</text>
                  <text x="%.2f" y="%.2f" class="edge-label">%s</text>
                """.formatted(
                geometry.labelX() - (labelWidth / 2.0d), geometry.labelY() - 19, labelWidth,
                geometry.labelX() - (labelWidth / 2.0d) + 8, geometry.labelY() - 5, escape(label),
                geometry.labelX() - (labelWidth / 2.0d) + 8, geometry.labelY() + 10, escape(detail)
        );
    }

    private static String resultsPanelSvg(JdbcPropertyGraphSample sample) {
        StringBuilder panel = new StringBuilder("""
                  <rect x="40" y="110" width="250" height="248" class="results-box"/>
                  <text x="58" y="140" class="results-header">GRAPH_TABLE Results</text>
                """);
        double y = 172;
        y = appendWrappedResult(panel, "Direct friends of Alice:", String.join(", ", sample.listDirectFriends("Alice")), y);
        y = appendWrappedResult(panel, "Within two hops:", String.join(", ", sample.listFriendsWithinTwoHops("Alice")), y + 12);
        appendWrappedResult(panel, "Recommended friends:", String.join(", ", sample.listRecommendedFriends("Alice")), y + 12);
        return panel.toString();
    }

    private static double appendWrappedResult(StringBuilder panel, String heading, String value, double y) {
        panel.append("""
                  <text x="58" y="%.2f" class="results-row">%s</text>
                """.formatted(y, escape(heading)));
        double lineY = y + 20;
        for (String line : wrap(value, 26)) {
            panel.append("""
                      <text x="58" y="%.2f" class="results-row">%s</text>
                    """.formatted(lineY, escape(line)));
            lineY += 18;
        }
        return lineY;
    }

    private static List<String> wrap(String value, int maxLineLength) {
        String[] words = value.split(", ");
        StringBuilder currentLine = new StringBuilder();
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + ", " + word;
            if (candidate.length() > maxLineLength && !currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(candidate);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static Map<String, JdbcPropertyGraphSample.Friendship> uniqueFriendships(List<JdbcPropertyGraphSample.Friendship> friendships) {
        LinkedHashMap<String, JdbcPropertyGraphSample.Friendship> unique = new LinkedHashMap<>();
        for (JdbcPropertyGraphSample.Friendship friendship : friendships) {
            int low = Math.min(friendship.person1Id(), friendship.person2Id());
            int high = Math.max(friendship.person1Id(), friendship.person2Id());
            String key = low + ":" + high;
            unique.putIfAbsent(key, friendship.person1Id() == low ? friendship : new JdbcPropertyGraphSample.Friendship(
                    friendship.friendshipId(),
                    low,
                    high,
                    friendship.sinceYear(),
                    friendship.strength()
            ));
        }
        return unique;
    }

    private static EdgeGeometry edgeGeometry(
            JdbcPropertyGraphSample.Friendship friendship,
            NodePosition source,
            NodePosition destination
    ) {
        double dx = destination.x() - source.x();
        double dy = destination.y() - source.y();
        double distance = Math.sqrt((dx * dx) + (dy * dy));
        double unitX = dx / distance;
        double unitY = dy / distance;
        double edgeOffset = friendship.person1Id() < friendship.person2Id() ? 28.0d : -28.0d;
        double normalX = -unitY;
        double normalY = unitX;
        NodePosition shiftedSource = new NodePosition(
                source.x() + (normalX * edgeOffset),
                source.y() + (normalY * edgeOffset)
        );
        NodePosition shiftedDestination = new NodePosition(
                destination.x() + (normalX * edgeOffset),
                destination.y() + (normalY * edgeOffset)
        );
        NodePosition start = projectToNodeBoundary(shiftedSource, unitX, unitY);
        NodePosition end = projectToNodeBoundary(shiftedDestination, -unitX, -unitY);
        double startX = start.x();
        double startY = start.y();
        double endX = end.x();
        double endY = end.y();
        double midX = (startX + endX) / 2.0d;
        double midY = (startY + endY) / 2.0d;
        double controlX = midX + (normalX * edgeOffset * 0.75d);
        double controlY = midY + (normalY * edgeOffset * 0.75d);
        double labelX = midX + (normalX * 46.0d);
        double labelY = midY + (normalY * 46.0d);
        return new EdgeGeometry(startX, startY, controlX, controlY, endX, endY, labelX, labelY);
    }

    private static NodePosition projectToNodeBoundary(NodePosition center, double directionX, double directionY) {
        double halfWidth = 62.0d;
        double topExtent = 18.0d;
        double bottomExtent = 80.0d;
        double scaleX = directionX == 0.0d ? Double.POSITIVE_INFINITY : halfWidth / Math.abs(directionX);
        double scaleY = directionY < 0.0d
                ? topExtent / Math.abs(directionY == 0.0d ? 1.0d : directionY)
                : directionY > 0.0d
                ? bottomExtent / directionY
                : Double.POSITIVE_INFINITY;
        double scale = Math.min(scaleX, scaleY);
        return new NodePosition(
                center.x() + (directionX * scale),
                center.y() + (directionY * scale)
        );
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private record NodePosition(double x, double y) {
    }

    private record EdgeGeometry(
            double startX,
            double startY,
            double controlX,
            double controlY,
            double endX,
            double endY,
            double labelX,
            double labelY
    ) {
    }
}
