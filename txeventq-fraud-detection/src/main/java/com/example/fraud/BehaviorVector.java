package com.example.fraud;

import java.time.Instant;
import java.time.ZoneOffset;

final class BehaviorVector {
    private BehaviorVector() {
    }

    static float[] from(CardChargeEvent event, String knownDeviceId) {
        int hour = Instant.parse(event.getOccurredAt()).atZone(ZoneOffset.UTC).getHour();
        float[] values = {
                (float) Math.min(event.getAmount() / 500d, 1d),
                categoryValue(event.getMerchantCategory(), "GROCERY"),
                categoryValue(event.getMerchantCategory(), "TRAVEL"),
                categoryValue(event.getMerchantCategory(), "DINING"),
                "CARD_PRESENT".equals(event.getChannel()) ? 1f : 0f,
                hour >= 8 && hour < 20 ? 1f : 0f,
                knownDeviceId.equals(event.getDeviceId()) ? 1f : 0f,
                "USD".equals(event.getCurrency()) ? 1f : 0f
        };
        return normalize(values);
    }

    static float[] normalize(float[] values) {
        double sum = 0;
        for (float value : values) {
            sum += value * value;
        }
        float magnitude = (float) Math.sqrt(sum);
        for (int index = 0; index < values.length; index++) {
            values[index] /= magnitude;
        }
        return values;
    }

    private static float categoryValue(String category, String expected) {
        return expected.equals(category) ? 1f : 0f;
    }
}
