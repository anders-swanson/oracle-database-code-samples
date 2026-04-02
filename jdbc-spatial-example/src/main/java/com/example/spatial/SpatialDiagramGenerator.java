package com.example.spatial;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import oracle.spatial.geometry.JGeometry;

/**
 * Generates a simple SVG diagram for the sample geometries and selected distances.
 */
public final class SpatialDiagramGenerator {
    private static final int WIDTH = 900;
    private static final int HEIGHT = 700;
    private static final int LEFT_PADDING = 80;
    private static final int RIGHT_PADDING = 80;
    private static final int TOP_PADDING = 150;
    private static final int BOTTOM_PADDING = 80;
    private static final double VIEW_MARGIN_RATIO = 0.08d;

    private SpatialDiagramGenerator() {
    }

    /**
     * Writes an SVG diagram to disk using the sample's stored geometries and exact distance calculations.
     */
    public static void writeSvg(JdbcSpatialExample sample, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        Files.writeString(outputFile, buildSvg(sample));
    }

    /**
     * Builds an SVG string that plots landmarks, the downtown window, and labeled distance lines.
     */
    public static String buildSvg(JdbcSpatialExample sample) {
        Feature ferryBuilding = feature(sample, "Ferry Building", "#0f766e");
        Feature coitTower = feature(sample, "Coit Tower", "#0f766e");
        Feature oraclePark = feature(sample, "Oracle Park", "#0f766e");
        Feature goldenGateBridge = feature(sample, "Golden Gate Bridge", "#0f766e");
        Feature downtownWindow = feature(sample, "Downtown Window", "#dc2626");

        List<Feature> features = List.of(ferryBuilding, coitTower, oraclePark, goldenGateBridge, downtownWindow);
        ViewBox viewBox = viewBox(features);

        StringBuilder svg = new StringBuilder();
        svg.append("""
                <svg xmlns="http://www.w3.org/2000/svg" width="900" height="700" viewBox="0 0 900 700">
                  <style>
                    text { font-family: "Helvetica Neue", Helvetica, Arial, sans-serif; fill: #111827; }
                    .title { font-size: 26px; font-weight: 700; }
                    .subtitle { font-size: 14px; fill: #4b5563; }
                    .landmark { fill: #0f766e; stroke: white; stroke-width: 2; }
                    .polygon { fill: rgba(220,38,38,0.08); stroke: #dc2626; stroke-width: 3; stroke-dasharray: 10 6; }
                    .distance { stroke: #2563eb; stroke-width: 2; stroke-dasharray: 7 5; }
                    .label { font-size: 14px; font-weight: 600; }
                    .distance-label { font-size: 13px; fill: #1d4ed8; }
                    .distance-label-box { fill: #f8fafc; stroke: #bfdbfe; stroke-width: 1; rx: 6; ry: 6; }
                    .legend { font-size: 13px; fill: #374151; }
                  </style>
                  <rect x="0" y="0" width="900" height="700" fill="#f8fafc"/>
                  <text x="70" y="48" class="title">Oracle Spatial Sample Diagram</text>
                  <text x="70" y="72" class="subtitle">Sample landmarks around San Francisco with exact Oracle Spatial distances in meters</text>
                """);

        svg.append(polygonSvg(downtownWindow, viewBox));
        svg.append(distanceSvg(sample, ferryBuilding, coitTower, viewBox, -10));
        svg.append(distanceSvg(sample, ferryBuilding, oraclePark, viewBox, 18));
        svg.append(distanceSvg(sample, ferryBuilding, goldenGateBridge, viewBox, -18));
        svg.append(distanceSvg(sample, coitTower, oraclePark, viewBox, 22));

        for (Feature feature : List.of(ferryBuilding, coitTower, oraclePark, goldenGateBridge)) {
            svg.append(pointSvg(feature, viewBox));
        }

        svg.append("""
                  <text x="70" y="660" class="legend">Dashed red box: Downtown Window polygon</text>
                  <text x="360" y="660" class="legend">Dashed blue lines: exact SDO_GEOM.SDO_DISTANCE results</text>
                </svg>
                """);
        return svg.toString();
    }

    /**
     * Loads one named feature from the database and derives a diagram center point from its bounding box.
     */
    private static Feature feature(JdbcSpatialExample sample, String name, String color) {
        JGeometry geometry = sample.getGeometry(name);
        double[] mbr = geometry.getMBR();
        return new Feature(name, geometry, (mbr[0] + mbr[2]) / 2.0d, (mbr[1] + mbr[3]) / 2.0d, color);
    }

    /**
     * Computes the overall plotting area from the sample geometries.
     */
    private static ViewBox viewBox(List<Feature> features) {
        double minLon = Double.POSITIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;

        for (Feature feature : features) {
            double[] mbr = feature.geometry().getMBR();
            minLon = Math.min(minLon, mbr[0]);
            minLat = Math.min(minLat, mbr[1]);
            maxLon = Math.max(maxLon, mbr[2]);
            maxLat = Math.max(maxLat, mbr[3]);
        }

        double lonMargin = Math.max((maxLon - minLon) * VIEW_MARGIN_RATIO, 0.01d);
        double latMargin = Math.max((maxLat - minLat) * VIEW_MARGIN_RATIO, 0.01d);
        return new ViewBox(
                minLon - lonMargin,
                minLat - latMargin,
                maxLon + lonMargin,
                maxLat + latMargin
        );
    }

    /**
     * Renders the sample polygon as a rectangle because the stored demo polygon is axis-aligned.
     */
    private static String polygonSvg(Feature polygon, ViewBox viewBox) {
        double[] mbr = polygon.geometry().getMBR();
        double left = scaleX(mbr[0], viewBox);
        double right = scaleX(mbr[2], viewBox);
        double top = scaleY(mbr[3], viewBox);
        double bottom = scaleY(mbr[1], viewBox);
        return """
                  <rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" class="polygon"/>
                  <text x="%.2f" y="%.2f" class="label">%s</text>
                """.formatted(
                left,
                top,
                right - left,
                bottom - top,
                left + 8,
                top - 10,
                polygon.name()
        );
    }

    /**
     * Renders one landmark point and its label.
     */
    private static String pointSvg(Feature feature, ViewBox viewBox) {
        double x = scaleX(feature.longitude(), viewBox);
        double y = scaleY(feature.latitude(), viewBox);
        return """
                  <circle cx="%.2f" cy="%.2f" r="8" class="landmark"/>
                  <text x="%.2f" y="%.2f" class="label">%s</text>
                """.formatted(x, y, x + 12, y - 12, feature.name());
    }

    /**
     * Renders a labeled distance line between two landmarks using the database-computed distance.
     */
    private static String distanceSvg(JdbcSpatialExample sample, Feature from, Feature to, ViewBox viewBox, double labelYOffset) {
        double x1 = scaleX(from.longitude(), viewBox);
        double y1 = scaleY(from.latitude(), viewBox);
        double x2 = scaleX(to.longitude(), viewBox);
        double y2 = scaleY(to.latitude(), viewBox);
        double labelX = (x1 + x2) / 2.0d;
        double labelY = (y1 + y2) / 2.0d + labelYOffset;
        String distanceLabel = "%.0f m".formatted(sample.distanceBetween(from.name(), to.name()));
        double labelWidth = 12 + (distanceLabel.length() * 7);

        return """
                  <line x1="%.2f" y1="%.2f" x2="%.2f" y2="%.2f" class="distance"/>
                  <rect x="%.2f" y="%.2f" width="%.2f" height="20" class="distance-label-box"/>
                  <text x="%.2f" y="%.2f" text-anchor="middle" dominant-baseline="middle" class="distance-label">%s</text>
                """.formatted(
                x1, y1, x2, y2,
                labelX - (labelWidth / 2.0d), labelY - 10,
                labelWidth,
                labelX, labelY + 1,
                distanceLabel
        );
    }

    /**
     * Maps a longitude to SVG space with a small outer margin.
     */
    private static double scaleX(double longitude, ViewBox viewBox) {
        double usableWidth = WIDTH - LEFT_PADDING - RIGHT_PADDING;
        double lonRange = viewBox.maxLongitude() - viewBox.minLongitude();
        return LEFT_PADDING + ((longitude - viewBox.minLongitude()) / lonRange) * usableWidth;
    }

    /**
     * Maps a latitude to SVG space and flips the Y axis for screen coordinates.
     */
    private static double scaleY(double latitude, ViewBox viewBox) {
        double usableHeight = HEIGHT - TOP_PADDING - BOTTOM_PADDING;
        double latRange = viewBox.maxLatitude() - viewBox.minLatitude();
        return HEIGHT - BOTTOM_PADDING - ((latitude - viewBox.minLatitude()) / latRange) * usableHeight;
    }

    private record Feature(String name, JGeometry geometry, double longitude, double latitude, String color) {
    }

    private record ViewBox(double minLongitude, double minLatitude, double maxLongitude, double maxLatitude) {
    }
}
