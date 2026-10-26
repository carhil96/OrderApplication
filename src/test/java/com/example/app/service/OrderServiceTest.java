package com.example.app.service;

import com.example.app.domain.Order;
import com.example.app.domain.Customer;
import com.example.app.domain.OutboxEvent;
import com.example.app.repository.CustomerRepository;
import com.example.app.repository.OutboxRepository;
import com.example.app.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

class OrderServiceTest {
  @Test
  void loadsOrdersForAllCustomerIdsInOneRepositoryCallAndKeepsEmptyGroups() {
    OrderRepository repository = mock(OrderRepository.class);
    CustomerRepository customers = mock(CustomerRepository.class);
    OutboxRepository outbox = mock(OutboxRepository.class);
    JsonMapper mapper = mock(JsonMapper.class);
    EntityManager entityManager = mock(EntityManager.class);
    OrderService service = new OrderService(repository, customers, outbox, mapper, entityManager);

    Customer ada = customer(1L, "Ada");
    Customer grace = customer(2L, "Grace");
    Order first = order(ada, new BigDecimal("10.50"));
    Order second = order(ada, new BigDecimal("12.00"));
    Order third = order(grace, new BigDecimal("20.00"));
    when(repository.findAllByCustomerRecordIdIn(anyList())).thenReturn(List.of(first, second, third));

    Map<Long, List<Order>> result = service.findOrdersByCustomerIds(Set.of(1L, 2L, 3L));

    assertEquals(List.of(first, second), result.get(1L));
    assertEquals(List.of(third), result.get(2L));
    assertEquals(List.of(), result.get(3L));
    verify(repository).findAllByCustomerRecordIdIn(anyList());
  }

  @Test
  void savesOrderAndOutboxEventInOneServiceCall() throws Exception {
    OrderRepository repository = mock(OrderRepository.class);
    CustomerRepository customers = mock(CustomerRepository.class);
    OutboxRepository outbox = mock(OutboxRepository.class);
    JsonMapper mapper = mock(JsonMapper.class);
    EntityManager entityManager = mock(EntityManager.class);
    Order order = new Order();
    order.setId(42);
    order.setCustomer("Ada");
    order.setAmount(new BigDecimal("10.50"));
    order.setCreatedAt(Instant.parse("2026-10-15T12:00:00Z"));
    order.setStatusOrder(Instant.parse("2026-10-15T12:00:00Z"));
    ReflectionTestUtils.setField(order, "version", 0L);
    Customer customer = new Customer("Ada");
    when(customers.findByName("Ada")).thenReturn(Optional.of(customer));
    when(repository.save(any(Order.class))).thenReturn(order);
    when(mapper.writeValueAsString(any())).thenReturn("serialized-event");
    when(mapper.readTree("serialized-event")).thenReturn(mock(JsonNode.class));
    OrderService service = new OrderService(repository, customers, outbox, mapper, entityManager);

    Order saved = service.createOrder(order);

    assertEquals("CREATED", saved.getStatus());
    assertEquals("Ada", saved.getCustomer());
    verify(repository).save(order);
    verify(entityManager).flush();
    verify(outbox).save(any(OutboxEvent.class));
  }

  private Customer customer(Long id, String name) {
    Customer customer = new Customer(name);
    ReflectionTestUtils.setField(customer, "id", id);
    return customer;
  }

  private Order order(Customer customer, BigDecimal amount) {
    Order order = new Order();
    order.setCustomerRecord(customer);
    order.setAmount(amount);
    order.setStatus("CREATED");
    return order;
  }
}
