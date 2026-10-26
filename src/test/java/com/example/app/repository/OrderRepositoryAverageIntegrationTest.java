package com.example.app.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import com.example.app.service.OrderService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://issuer.example.test/jwks"
})
@Transactional
class OrderRepositoryAverageIntegrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private OrderRepository orderRepository;

  @MockitoBean
  private OrderService orderService;

  /** Comprueba el promedio SQL y los límites inclusivo/exclusivo del intervalo. */
  @Test
  void averagesAmountsInsideHalfOpenRange() {
    Instant from = Instant.parse("2099-04-01T00:00:00Z");
    Instant to = Instant.parse("2099-05-01T00:00:00Z");
    insertOrder("10.00", from);
    insertOrder("30.00", Instant.parse("2099-04-15T12:00:00Z"));
    insertOrder("90.00", to);

    BigDecimal average = orderRepository.averageAmountCreatedBetween(from, to);

    assertEquals(0, new BigDecimal("20.00").compareTo(average));
  }

  /** Comprueba que PostgreSQL devuelve null si el intervalo no contiene pedidos. */
  @Test
  void returnsNullWhenRangeHasNoOrders() {
    BigDecimal average = orderRepository.averageAmountCreatedBetween(
        Instant.parse("2100-01-01T00:00:00Z"), Instant.parse("2100-02-01T00:00:00Z"));

    assertNull(average);
  }

  private void insertOrder(String amount, Instant createdAt) {
    jdbcTemplate.update("INSERT INTO customers(name) VALUES (?) ON CONFLICT(name) DO NOTHING",
        "average-integration-test");
    Long customerId = jdbcTemplate.queryForObject(
        "SELECT id FROM customers WHERE name = ?", Long.class, "average-integration-test");
    jdbcTemplate.update(
        "INSERT INTO orders (customer_id, amount, status, created_at, status_order) VALUES (?, ?, ?, ?, ?)",
        customerId, new BigDecimal(amount), "CREATED", Timestamp.from(createdAt), Timestamp.from(createdAt));
  }
}
