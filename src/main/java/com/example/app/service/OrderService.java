package com.example.app.service;

import com.example.app.domain.Order;
import com.example.app.dto.OrderEvent;
import com.example.app.repository.OrderRepository;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private final OrderRepository repo;
  private final KafkaTemplate<String, String> kafka;
  private final JsonMapper mapper;
  private final String topic;

  /** Recibe los componentes necesarios para guardar y publicar pedidos. */
  public OrderService(OrderRepository repo, KafkaTemplate<String, String> kafka, JsonMapper mapper,
      @Value("${kafka.topic:orders-topic}") String topic) {
    this.repo = repo;
    this.kafka = kafka;
    this.mapper = mapper;
    this.topic = topic;
  }

  /** Guarda el pedido en PostgreSQL y publica su copia asíncrona en Kafka. */
  @Transactional
  public Order createOrder(Order order) {
    order.setStatus("CREATED");
    Order saved = repo.save(order);

    try {
      OrderEvent ev = new OrderEvent(saved.getId(), saved.getCustomer(), saved.getAmount(),
          saved.getStatus(), saved.getCreatedAt());
      String json = mapper.writeValueAsString(ev);
      kafka.send(topic, String.valueOf(saved.getId()), json)
          .whenComplete((result, error) -> {
            if (error != null) {
              log.error("Error publishing order {} to Kafka; ClickHouse copy may be missing", saved.getId(), error);
            }
          });
    } catch (Exception e) {
      log.error("Error publishing event", e);
    }

    return saved;
  }
}
