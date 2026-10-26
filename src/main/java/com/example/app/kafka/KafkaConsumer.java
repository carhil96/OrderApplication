package com.example.app.kafka;

import com.example.app.dto.OrderEvent;
import com.example.app.dto.OutboxPayload;
import com.example.app.dto.OrderSnapshot;
import com.example.app.dto.CustomerSnapshot;
import com.example.app.exception.InvalidOrderEventException;
import com.example.app.service.ClickHouseWriter;
import com.example.app.util.DateTimeUtils;
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

  @KafkaListener(topics = "${kafka.outbox.order-topic:outbox.event.order}", groupId = "${spring.kafka.consumer.group-id:orders-group}")
  public void listenOrderOutbox(String raw) {
    OutboxPayload event = readOutbox(raw);
    var data = event.data();
    if (data == null || data.path("id").isMissingNode()) {
      throw new InvalidOrderEventException("Order outbox event is missing its data: " + raw);
    }
    OrderSnapshot snapshot = new OrderSnapshot(data.path("id").intValue(), data.path("customerId").longValue(),
        data.path("customer").asString(), data.path("amount").decimalValue(),
        data.path("status").asString(), DateTimeUtils.toInstant(data.path("createdAt").asString()),
        DateTimeUtils.toInstant(data.path("statusOrder").asString()), event.aggregateVersion());
    clickHouseWriter.persist(snapshot);
    log.info("Persisted outbox order {} version {} to ClickHouse", snapshot.id(), snapshot.version());
  }

  @KafkaListener(topics = "${kafka.outbox.customer-topic:outbox.event.customer}", groupId = "${spring.kafka.consumer.group-id:orders-group}")
  public void listenCustomerOutbox(String raw) {
    OutboxPayload event = readOutbox(raw);
    var data = event.data();
    if (data == null || data.path("id").isMissingNode()) {
      throw new InvalidOrderEventException("Customer outbox event is missing its data: " + raw);
    }
    String name = data.path("name").isNull() ? null : data.path("name").asString();
    CustomerSnapshot snapshot = new CustomerSnapshot(data.path("id").longValue(), name,
        event.aggregateVersion(), data.path("deleted").asBoolean(false));
    clickHouseWriter.persist(snapshot);
    log.info("Persisted outbox customer {} version {} to ClickHouse", snapshot.id(), snapshot.version());
  }

  private OutboxPayload readOutbox(String raw) {
    try {
      OutboxPayload event = mapper.readValue(raw, OutboxPayload.class);
      if (event == null || event.aggregateVersion() < 0) {
        throw new InvalidOrderEventException("Invalid outbox event: " + raw);
      }
      return event;
    } catch (InvalidOrderEventException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidOrderEventException("Invalid outbox event from topic " + topic, e);
    }
  }

}
