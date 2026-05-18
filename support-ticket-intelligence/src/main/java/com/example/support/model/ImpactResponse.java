package com.example.support.model;

import java.util.List;

public record ImpactResponse(long ticketId, List<ImpactPath> paths) {
}
