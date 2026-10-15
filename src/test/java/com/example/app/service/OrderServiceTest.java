package com.example.app.service;

import com.example.app.domain.Order;
import com.example.app.dto.OrderEvent;
import com.example.app.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {
  @Test
  void savesSynchronouslyThenPublishesCompleteEventWithoutWritingClickHouseInline() throws Exception {
    OrderRepository repository = mock(OrderRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    JsonMapper mapper = mock(JsonMapper.class);
    Order order = new Order();
    order.setId(42);
    order.setCustomer("Ada");
    order.setAmount(new BigDecimal("10.50"));
    order.setCreatedAt(Instant.parse("2026-10-15T12:00:00Z"));
    when(repository.save(any(Order.class))).thenReturn(order);
    when(mapper.writeValueAsString(any())).thenReturn("serialized-order");
    when(kafka.send("orders-topic", "42", "serialized-order"))
        .thenReturn(CompletableFuture.completedFuture(null));
    OrderService service = new OrderService(repository, kafka, mapper, "orders-topic");

    Order saved = service.createOrder(order);

    assertEquals("CREATED", saved.getStatus());
    ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
    verify(mapper).writeValueAsString(eventCaptor.capture());
    assertEquals(42, eventCaptor.getValue().getId());
    assertEquals("CREATED", eventCaptor.getValue().getStatus());
    assertEquals(order.getCreatedAt(), eventCaptor.getValue().getCreatedAt());
    inOrder(repository, kafka).verify(repository).save(order);
    inOrder(repository, kafka).verify(kafka).send("orders-topic", "42", "serialized-order");
  }
}
