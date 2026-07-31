package com.example.fraud;

/** JSON event sent through the CARD_CHARGES OKafka topic. */
public class CardChargeEvent {
    private long transactionId;
    private long cardholderId;
    private String occurredAt;
    private double amount;
    private String currency;
    private String merchantName;
    private String merchantCategory;
    private String channel;
    private String deviceId;
    private double latitude;
    private double longitude;

    public CardChargeEvent() {}

    public CardChargeEvent(long transactionId, long cardholderId, String occurredAt, double amount,
                           String currency, String merchantName, String merchantCategory, String channel,
                           String deviceId, double latitude, double longitude) {
        this.transactionId = transactionId;
        this.cardholderId = cardholderId;
        this.occurredAt = occurredAt;
        this.amount = amount;
        this.currency = currency;
        this.merchantName = merchantName;
        this.merchantCategory = merchantCategory;
        this.channel = channel;
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String toSemanticString() {
        return "%s %s charge of %.2f at %s in the %s category via %s using device %s"
                .formatted(currency, cardholderId, amount, merchantName, merchantCategory, channel, deviceId);
    }

    public long getTransactionId() { return transactionId; }
    public void setTransactionId(long transactionId) { this.transactionId = transactionId; }
    public long getCardholderId() { return cardholderId; }
    public void setCardholderId(long cardholderId) { this.cardholderId = cardholderId; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getMerchantCategory() { return merchantCategory; }
    public void setMerchantCategory(String merchantCategory) { this.merchantCategory = merchantCategory; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}
