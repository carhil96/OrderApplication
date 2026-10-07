package com.example.app.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {
  private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);
  @KafkaListener(topics = "orders-topic", groupId = "orders-group")
  public void listen(String raw) {
    try {
      // En un caso real deserializar en DTO y aplicar lógica de negocio.
      log.info("Received kafka message: {}", raw);
    } catch (Exception e) {
      log.error("Error processing kafka message", e);
    }
  }
}
