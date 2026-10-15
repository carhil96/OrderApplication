package com.example.app.kafka;

import com.example.app.dto.OrderEvent;
import com.example.app.service.ClickHouseWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

class KafkaConsumerTest {
  private final ClickHouseWriter clickHouseWriter = mock(ClickHouseWriter.class);
  private final JsonMapper mapper = mock(JsonMapper.class);
  private final KafkaConsumer consumer = new KafkaConsumer(clickHouseWriter, mapper, "orders-topic");

  @Test
  void writesValidEventToClickHouseAndFillsFieldsMissingFromLegacyEvents() throws Exception {
    OrderEvent legacyEvent = new OrderEvent(42, "Ada", new BigDecimal("10.50"));
    when(mapper.readValue("legacy-event", OrderEvent.class)).thenReturn(legacyEvent);

    consumer.listen("legacy-event");

    assertEquals("CREATED", legacyEvent.getStatus());
    assertNotNull(legacyEvent.getCreatedAt());
    verify(clickHouseWriter).persist(legacyEvent);
  }

  @Test
  void routesMalformedOrIncompleteEventsToTheInvalidEventHandler() throws Exception {
    when(mapper.readValue(eq("{}"), eq(OrderEvent.class))).thenReturn(new OrderEvent());

    assertThrows(InvalidOrderEventException.class, () -> consumer.listen("{}"));
  }

  @Test
  void propagatesClickHouseFailuresSoKafkaCanRetry() throws Exception {
    OrderEvent event = new OrderEvent(42, "Ada", new BigDecimal("10.50"), "CREATED", Instant.now());
    when(mapper.readValue("valid-event", OrderEvent.class)).thenReturn(event);
    RuntimeException failure = new RuntimeException("ClickHouse unavailable");
    org.mockito.Mockito.doThrow(failure).when(clickHouseWriter).persist(event);

    RuntimeException actual = assertThrows(RuntimeException.class, () -> consumer.listen("valid-event"));

    assertEquals(failure, actual);
  }
}
