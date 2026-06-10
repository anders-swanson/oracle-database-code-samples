package com.example.security;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSecHandoffTest {
    @Test
    void routerServiceRoleIsEnabledOnlyDuringElevation() {
        assertThat(SupportActor.ROUTER.dataRoles(false)).isEmpty();
        assertThat(SupportActor.ROUTER.dataRoles(true)).containsExactly("support_service_role");
    }

    @Test
    void parsesModeArgumentWithOrWithoutCommandLinePrefix() {
        assertThat(SecurityMode.parse("compat")).isEqualTo(SecurityMode.COMPAT);
        assertThat(SecurityMode.parse("--mode=auto")).isEqualTo(SecurityMode.AUTO);
        assertThat(SecurityMode.parse("--mode=deepsec")).isEqualTo(SecurityMode.DEEPSEC);
    }

    @Test
    void fallsBackOnlyWhenDeepSecProbeFeatureIsUnsupported() {
        assertThat(DeepSecDetector.isUnsupportedFeature(new SQLException("invalid identifier", "42000", 904)))
                .isTrue();
        assertThat(DeepSecDetector.isUnsupportedFeature(new SQLException("invalid identifier", "42000", -904)))
                .isTrue();
        assertThat(DeepSecDetector.isUnsupportedFeature(new SQLException("insufficient privileges", "42000", 1031)))
                .isFalse();
    }
}
