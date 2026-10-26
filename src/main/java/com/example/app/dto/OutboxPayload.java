package com.example.app.dto;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record OutboxPayload(String eventType, String aggregateId, long aggregateVersion,
                            Instant occurredAt, JsonNode data) {}
