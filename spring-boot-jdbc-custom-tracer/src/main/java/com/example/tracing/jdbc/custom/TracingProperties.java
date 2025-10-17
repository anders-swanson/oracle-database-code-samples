package com.example.tracing.jdbc.custom;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Properties;

@ConfigurationProperties(prefix = TracingProperties.PREFIX)
public class TracingProperties {
    public static final String PREFIX = "management.tracing.ojdbc";

    private boolean enabled = true;
    private boolean showSensitiveData = false;
    private boolean includeClientInfo = false;

    private Properties clientInfo = new Properties();

    public static TracingProperties defaultProperties() {
        TracingProperties props = new TracingProperties();

        return props;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isShowSensitiveData() {
        return showSensitiveData;
    }

    public void setShowSensitiveData(boolean showSensitiveData) {
        this.showSensitiveData = showSensitiveData;
    }

    public boolean isIncludeClientInfo() {
        return includeClientInfo;
    }

    public void setIncludeClientInfo(boolean includeClientInfo) {
        this.includeClientInfo = includeClientInfo;
    }

    public Properties getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(Properties clientInfo) {
        this.clientInfo = clientInfo;
    }
}
