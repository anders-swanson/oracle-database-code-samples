package com.example.kafka;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class WeatherEvent {
    private Long _id;
    private Station station;
    private LocalDateTime timestamp;
    private double temperature;
    private double humidityPercent;
    private double uvIndex;

    public WeatherEvent(Long stationId, LocalDateTime timestamp, double temperature, double humidityPercent, double uvIndex) {
        station = new Station();
        this.station.setId(stationId);
        this.timestamp = timestamp;
        this.temperature = temperature;
        this.humidityPercent = humidityPercent;
        this.uvIndex = uvIndex;
    }

    // Nested Station class
    public static class Station {
        private Long id;

        public Station() {}

        public Station(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Station{" +
                    "id=" + id +
                    '}';
        }
    }

    // Getters and setters
    public Long get_id() {
        return _id;
    }

    public void set_id(Long _id) {
        this._id = _id;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getHumidityPercent() {
        return humidityPercent;
    }

    public void setHumidityPercent(double humidityPercent) {
        this.humidityPercent = humidityPercent;
    }

    public double getUvIndex() {
        return uvIndex;
    }

    public void setUvIndex(double uvIndex) {
        this.uvIndex = uvIndex;
    }

    @Override
    public String toString() {
        return "WeatherEvent{" +
                "station=" + station +
                ", timestamp=" + timestamp +
                ", temperature=" + temperature +
                ", humidity_percent=" + humidityPercent +
                ", uv_index=" + uvIndex +
                '}';
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof WeatherEvent that)) return false;

        return Double.compare(getTemperature(), that.getTemperature()) == 0 && Double.compare(getHumidityPercent(), that.getHumidityPercent()) == 0 && Double.compare(getUvIndex(), that.getUvIndex()) == 0 && Objects.equals(get_id(), that.get_id()) && Objects.equals(getStation(), that.getStation()) && Objects.equals(getTimestamp(), that.getTimestamp());
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(get_id());
        result = 31 * result + Objects.hashCode(getStation());
        result = 31 * result + Objects.hashCode(getTimestamp());
        result = 31 * result + Double.hashCode(getTemperature());
        result = 31 * result + Double.hashCode(getHumidityPercent());
        result = 31 * result + Double.hashCode(getUvIndex());
        return result;
    }

    public static List<WeatherEvent> getSampleEvents() {
        return Arrays.asList(
                new WeatherEvent(1L, LocalDateTime.parse("2025-06-03T06:00:00"), 18.2, 81.0, 0.0),
                new WeatherEvent(1L, LocalDateTime.parse("2025-06-03T08:00:00"), 21.7, 70.3, 2.5),
                new WeatherEvent(2L, LocalDateTime.parse("2025-06-03T10:00:00"), 25.4, 62.8, 4.9),
                new WeatherEvent(2L, LocalDateTime.parse("2025-06-03T12:00:00"), 28.6, 51.2, 7.3),
                new WeatherEvent(3L, LocalDateTime.parse("2025-06-03T14:00:00"), 31.6, 40.5, 10.5),
                new WeatherEvent(3L, LocalDateTime.parse("2025-06-03T16:00:00"), 30.1, 45.1, 9.0),
                new WeatherEvent(1L, LocalDateTime.parse("2025-06-03T18:00:00"), 27.0, 57.0, 3.8),
                new WeatherEvent(2L, LocalDateTime.parse("2025-06-03T20:00:00"), 23.5, 63.3, 0.9),
                new WeatherEvent(3L, LocalDateTime.parse("2025-06-03T22:00:00"), 20.8, 69.1, 0.0),
                new WeatherEvent(1L, LocalDateTime.parse("2025-06-04T00:00:00"), 18.6, 74.8, 0.0)
        );
    }
}
