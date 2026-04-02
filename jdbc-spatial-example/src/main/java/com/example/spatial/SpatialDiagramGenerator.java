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
    private static final int LEFT_PADDING = 330;
    private static final int RIGHT_PADDING = 70;
    private static final int TOP_PADDING = 150;
    private static final int BOTTOM_PADDING = 80;
    private static final double VIEW_MARGIN_RATIO = 0.08d;

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
                    .point-label-box { fill: rgba(248,250,252,0.92); stroke: #cbd5e1; stroke-width: 1; rx: 6; ry: 6; }
                    .label { font-size: 14px; font-weight: 600; }
                    .distance-table { fill: #f8fafc; stroke: #bfdbfe; stroke-width: 1; rx: 10; ry: 10; }
                    .distance-table-header { font-size: 13px; font-weight: 700; fill: #1e3a8a; }
                    .distance-table-row { font-size: 12px; fill: #1f2937; }
                    .distance-table-value { font-size: 12px; font-weight: 700; fill: #1d4ed8; }
                    .legend { font-size: 13px; fill: #374151; }
                  </style>
                  <rect x="0" y="0" width="900" height="700" fill="#f8fafc"/>
                  <text x="70" y="48" class="title">Oracle Spatial Sample Diagram</text>
                  <text x="70" y="72" class="subtitle">Sample landmarks around San Francisco with exact Oracle Spatial distances in meters</text>
                """);

        svg.append(distanceTableSvg(sample, List.of(
                new DistanceMeasurement(ferryBuilding, coitTower),
                new DistanceMeasurement(ferryBuilding, oraclePark),
                new DistanceMeasurement(ferryBuilding, goldenGateBridge),
                new DistanceMeasurement(coitTower, oraclePark)
        )));
        svg.append(polygonSvg(downtownWindow, viewBox));
        svg.append(distanceLineSvg(ferryBuilding, coitTower, viewBox));
        svg.append(distanceLineSvg(ferryBuilding, oraclePark, viewBox));
        svg.append(distanceLineSvg(ferryBuilding, goldenGateBridge, viewBox));
        svg.append(distanceLineSvg(coitTower, oraclePark, viewBox));

        for (Feature feature : List.of(ferryBuilding, coitTower, oraclePark, goldenGateBridge)) {
            svg.append(pointSvg(feature, viewBox));
        }

        svg.append("""
                  <text x="70" y="660" class="legend">Dashed red box: Downtown Window polygon</text>
                  <text x="360" y="660" class="legend">Dashed blue lines: measured feature-to-feature distances</text>
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
        PointLabelOffset offset = pointLabelOffset(feature.name());
        double labelX = x + offset.dx();
        double labelY = y + offset.dy();
        double labelWidth = 20 + (feature.name().length() * 7.2);
        return """
                  <circle cx="%.2f" cy="%.2f" r="8" class="landmark"/>
                  <rect x="%.2f" y="%.2f" width="%.2f" height="22" class="point-label-box"/>
                  <text x="%.2f" y="%.2f" class="label">%s</text>
                """.formatted(
                x, y,
                labelX - 10, labelY - 16, labelWidth,
                labelX, labelY,
                feature.name()
        );
    }

    /**
     * Renders a dashed distance line directly on the map.
     */
    private static String distanceLineSvg(Feature from, Feature to, ViewBox viewBox) {
        double x1 = scaleX(from.longitude(), viewBox);
        double y1 = scaleY(from.latitude(), viewBox);
        double x2 = scaleX(to.longitude(), viewBox);
        double y2 = scaleY(to.latitude(), viewBox);
        return """
                  <line x1="%.2f" y1="%.2f" x2="%.2f" y2="%.2f" class="distance"/>
                """.formatted(x1, y1, x2, y2);
    }

    /**
     * Renders a small distance table to the left of the map.
     */
    private static String distanceTableSvg(JdbcSpatialExample sample, List<DistanceMeasurement> measurements) {
        double x = 40;
        double y = 110;
        double width = 250;
        double rowHeight = 46;
        double height = 46 + (measurements.size() * rowHeight);

        StringBuilder table = new StringBuilder("""
                  <rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" class="distance-table"/>
                  <text x="58" y="138" class="distance-table-header">Measured Distances</text>
                """.formatted(x, y, width, height));

        for (int i = 0; i < measurements.size(); i++) {
            DistanceMeasurement measurement = measurements.get(i);
            double rowY = y + 46 + (i * rowHeight);
            double distance = sample.distanceBetween(measurement.from().name(), measurement.to().name());
            table.append("""
                      <text x="58" y="%.2f" class="distance-table-row">%s to %s</text>
                      <text x="58" y="%.2f" class="distance-table-value">%.0f m</text>
                    """.formatted(
                    rowY,
                    measurement.from().name(),
                    measurement.to().name(),
                    rowY + 18,
                    distance
            ));
        }

        return table.toString();
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

    /**
     * Uses fixed offsets so point labels stay clear of the distance callouts.
     */
    private static PointLabelOffset pointLabelOffset(String featureName) {
        return switch (featureName) {
            case "Ferry Building" -> new PointLabelOffset(14, -18);
            case "Coit Tower" -> new PointLabelOffset(14, -20);
            case "Oracle Park" -> new PointLabelOffset(14, 28);
            case "Golden Gate Bridge" -> new PointLabelOffset(14, -18);
            default -> new PointLabelOffset(12, -12);
        };
    }

    private record Feature(String name, JGeometry geometry, double longitude, double latitude, String color) {
    }

    private record DistanceMeasurement(Feature from, Feature to) {
    }

    private record PointLabelOffset(double dx, double dy) {
    }

    private record ViewBox(double minLongitude, double minLatitude, double maxLongitude, double maxLatitude) {
    }
}
