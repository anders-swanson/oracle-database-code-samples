package com.example.security;

import java.util.List;

enum SupportActor {
    ALICE("alice@example.com", "Alice", "ACME", "WEST", "AGENT", List.of("support_agent_role")),
    BOB("bob@example.com", "Bob", "ACME", "EAST", "AGENT", List.of("support_agent_role")),
    MARIA("maria@example.com", "Maria", "ACME", "WEST", "MANAGER", List.of("support_manager_role")),
    ROUTER("router-service", "Routing Service", "SERVICE", "GLOBAL", "SERVICE", List.of());

    private final String username;
    private final String displayName;
    private final String tenantId;
    private final String region;
    private final String role;
    private final List<String> baseDataRoles;

    SupportActor(
            String username,
            String displayName,
            String tenantId,
            String region,
            String role,
            List<String> baseDataRoles
    ) {
        this.username = username;
        this.displayName = displayName;
        this.tenantId = tenantId;
        this.region = region;
        this.role = role;
        this.baseDataRoles = baseDataRoles;
    }

    String username() {
        return username;
    }

    String displayName() {
        return displayName;
    }

    String tenantId() {
        return tenantId;
    }

    String region() {
        return region;
    }

    String role() {
        return role;
    }

    List<String> dataRoles(boolean elevated) {
        if (this == ROUTER && elevated) {
            return List.of("support_service_role");
        }
        return baseDataRoles;
    }
}
