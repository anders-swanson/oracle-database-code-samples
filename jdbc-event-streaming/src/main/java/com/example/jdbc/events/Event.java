package com.example.jdbc.events;

public class Event {
    private double temperature;
    private double humidity;
    private double latitude;
    private double longitude;

    public Event() {}

    public Event(double temperature, double humidity, double latitude, double longitude) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getHumidity() {
        return humidity;
    }

    public void setHumidity(double humidity) {
        this.humidity = humidity;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Event event)) return false;

        return Double.compare(getTemperature(), event.getTemperature()) == 0 && Double.compare(getHumidity(), event.getHumidity()) == 0 && Double.compare(getLatitude(), event.getLatitude()) == 0 && Double.compare(getLongitude(), event.getLongitude()) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(getTemperature());
        result = 31 * result + Double.hashCode(getHumidity());
        result = 31 * result + Double.hashCode(getLatitude());
        result = 31 * result + Double.hashCode(getLongitude());
        return result;
    }
}
