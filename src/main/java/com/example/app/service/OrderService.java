package com.example.app.service;

import com.example.app.domain.Order;
import com.example.app.dto.OrderSnapshot;
import com.example.app.dto.OutboxPayload;
import com.example.app.domain.Customer;
import com.example.app.domain.OutboxEvent;
import com.example.app.repository.CustomerRepository;
import com.example.app.repository.OutboxRepository;
import com.example.app.repository.OrderRepository;
import tools.jackson.databind.json.JsonMapper;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderService {
  private final OrderRepository repo;
  private final CustomerRepository customers;
  private final OutboxRepository outbox;
  private final JsonMapper mapper;
  private final EntityManager entityManager;

  /** Recibe los componentes necesarios para persistir pedidos y sus eventos Outbox. */
  public OrderService(OrderRepository repo, CustomerRepository customers, OutboxRepository outbox,
      JsonMapper mapper, EntityManager entityManager) {
    this.repo = repo;
    this.customers = customers;
    this.outbox = outbox;
    this.mapper = mapper;
    this.entityManager = entityManager;
  }

  /** Guarda el pedido y su evento Outbox en la misma transacción PostgreSQL. */
  @Transactional
  public Order createOrder(Order order) {
    String customerName = order.getCustomer();
    if (customerName == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customer is required");
    }
    Customer customer = customers.findByName(customerName)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "customer does not exist"));
    order.setCustomerRecord(customer);
    order.setStatus("CREATED");
    Order saved = repo.save(order);
    entityManager.flush();
    appendOutbox(saved, "OrderCreated");
    return saved;
  }

  private OrderSnapshot snapshot(Order order) {
    return new OrderSnapshot(order.getId(), order.getCustomerRecord().getId(),
        order.getCustomerRecord().getName(), order.getAmount(), order.getStatus(),
        order.getCreatedAt(), order.getStatusOrder(), order.getVersion());
  }

  private void appendOutbox(Order order, String type) {
    try {
      String aggregateId = order.getId().toString();
      Long version = order.getVersion();
      OutboxPayload payload = new OutboxPayload(type, aggregateId, version,
          order.getStatusOrder(), mapper.readTree(mapper.writeValueAsString(snapshot(order))));
      outbox.save(new OutboxEvent("order", aggregateId, type, version,
          mapper.readTree(mapper.writeValueAsString(payload)), Instant.now()));
    } catch (Exception e) {
      throw new IllegalStateException("Could not serialize outbox event " + type, e);
    }
  }

  @Transactional(readOnly = true)
  public List<Order> findAll() {
    List<Order> found = repo.findAll();
    found.forEach(Order::getCustomer);
    return found;
  }

  @Transactional(readOnly = true)
  public Order findById(Integer id) {
    Order found = repo.findById(id).orElse(null);
    if (found != null) found.getCustomer();
    return found;
  }

  /** Carga y agrupa pedidos para todas las claves recibidas en una consulta. */
  @Transactional(readOnly = true)
  public Map<Long, List<Order>> findOrdersByCustomerIds(Set<Long> customerIds) {
    Map<Long, List<Order>> grouped = new LinkedHashMap<>();
    customerIds.forEach(customerId -> grouped.put(customerId, new ArrayList<>()));

    if (customerIds.isEmpty()) {
      return grouped;
    }

    List<Order> found = repo.findAllByCustomerRecordIdIn(List.copyOf(customerIds));
    found.forEach(order -> grouped.get(order.getCustomerRecord().getId()).add(order));
    return grouped;
  }

  @Transactional
  public Order updateStatus(Integer id, String requestedStatus) {
    if (!"DELIVERED".equals(requestedStatus)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only CREATED to DELIVERED is supported");
    }
    Order order = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    if ("DELIVERED".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "order is already DELIVERED");
    }
    if (!"CREATED".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid order status transition");
    }
    order.setStatus("DELIVERED");
    order.setStatusOrder(Instant.now());
    entityManager.flush();
    appendOutbox(order, "OrderStatusChanged");
    return order;
  }

  /** Devuelve el promedio de los importes de pedidos creados en [from, to). */
  @Transactional(readOnly = true)
  public BigDecimal averageAmountCreatedBetween(Instant from, Instant to) {
    return repo.averageAmountCreatedBetween(from, to);
  }
}
