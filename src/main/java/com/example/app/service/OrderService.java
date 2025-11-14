package com.example.app.service;

import com.example.app.domain.Order;
import com.example.app.dto.OrderEvent;
import com.example.app.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private final OrderRepository repo;
  private final KafkaTemplate<String, String> kafka;
  private final ClickHouseWriter clickHouseWriter;
  private final ObjectMapper mapper = new ObjectMapper();

  public OrderService(OrderRepository repo, KafkaTemplate<String, String> kafka, ClickHouseWriter clickHouseWriter) {
    this.repo = repo;
    this.kafka = kafka;
    this.clickHouseWriter = clickHouseWriter;
  }

  @Transactional
  public Order createOrder(Order order) {
    order.setStatus("CREATED");
    Order saved = repo.save(order);

    try {
      OrderEvent ev = new OrderEvent(saved.getId(), saved.getCustomer(), saved.getAmount());
      String json = mapper.writeValueAsString(ev);
      kafka.send(System.getenv().getOrDefault("KAFKA_TOPIC","orders-topic"), String.valueOf(saved.getId()), json);
    } catch (Exception e) {
      log.error("Error publishing event", e);
    }

    clickHouseWriter.enqueue(saved);
    return saved;
  }
}
