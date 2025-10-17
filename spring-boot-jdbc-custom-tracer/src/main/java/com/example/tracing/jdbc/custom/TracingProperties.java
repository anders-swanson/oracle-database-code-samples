package com.example.tracing.jdbc.custom;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = TracingProperties.PREFIX)
public class TracingProperties {
    public static final String PREFIX = "management.tracing.ojdbc";

    private boolean enabled = true;
    private boolean showSensitiveData = false;
    private boolean includeClientInfo = false;
    private boolean includeSystemUsername = false;

    private List<String> clientInfoKeys = new ArrayList<>();

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

    public boolean isIncludeSystemUsername() {
        return includeSystemUsername;
    }

    public void setIncludeSystemUsername(boolean includeSystemUsername) {
        this.includeSystemUsername = includeSystemUsername;
    }

    public List<String> getClientInfoKeys() {
        return clientInfoKeys;
    }

    public void setClientInfoKeys(List<String> clientInfoKeys) {
        this.clientInfoKeys = clientInfoKeys;
    }
}
