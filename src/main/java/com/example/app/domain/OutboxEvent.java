package com.example.app.domain;

import jakarta.persistence.*;
import tools.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxEvent {
  @Id
  private UUID id;
  @Column(name = "aggregatetype", nullable = false, length = 100)
  private String aggregateType;
  @Column(name = "aggregateid", nullable = false, length = 100)
  private String aggregateId;
  @Column(nullable = false, length = 150)
  private String type;
  @Column(name = "aggregateversion", nullable = false)
  private Long aggregateVersion;
  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private JsonNode payload;
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected OutboxEvent() {}

  public OutboxEvent(String aggregateType, String aggregateId, String type,
      Long aggregateVersion, JsonNode payload, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.type = type;
    this.aggregateVersion = aggregateVersion;
    this.payload = payload;
    this.createdAt = createdAt;
  }
}
