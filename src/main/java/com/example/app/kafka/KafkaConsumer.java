package com.example.app.kafka;

import com.example.app.dto.OrderEvent;
import com.example.app.exception.InvalidOrderEventException;
import com.example.app.service.ClickHouseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
public class KafkaConsumer {
  private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

  private final ClickHouseWriter clickHouseWriter;
  private final JsonMapper mapper;
  private final String topic;

  public KafkaConsumer(ClickHouseWriter clickHouseWriter, JsonMapper mapper,
      @Value("${kafka.topic:orders-topic}") String topic) {
    this.clickHouseWriter = clickHouseWriter;
    this.mapper = mapper;
    this.topic = topic;
  }

  /** Persiste el evento en ClickHouse; los errores se propagan para reintento Kafka. */
  @KafkaListener(topics = "${kafka.topic:orders-topic}", groupId = "${spring.kafka.consumer.group-id:orders-group}")
  public void listen(String raw) {
    OrderEvent event;
    try {
      event = mapper.readValue(raw, OrderEvent.class);
    } catch (Exception e) {
      throw new InvalidOrderEventException("Invalid order event from topic " + topic, e);
    }

    if (event == null || event.getId() == null || event.getCustomer() == null || event.getAmount() == null) {
      throw new InvalidOrderEventException("Order event is missing required fields: " + raw);
    }
    if (event.getStatus() == null || event.getStatus().isBlank()) {
      event.setStatus("CREATED");
    }
    if (event.getCreatedAt() == null) {
      event.setCreatedAt(Instant.now());
    }

    clickHouseWriter.persist(event);
    log.info("Persisted Kafka order {} to ClickHouse", event.getId());
  }
}
