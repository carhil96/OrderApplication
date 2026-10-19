package com.example.app.repository;

import com.example.app.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;

/** Proporciona las operaciones CRUD de pedidos mediante Spring Data JPA. */
@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

  /** Calcula en PostgreSQL el promedio de importe dentro del intervalo [from, to). */
  @Query(value = "SELECT AVG(amount) FROM orders WHERE created_at >= :from AND created_at < :to", nativeQuery = true)
  BigDecimal averageAmountCreatedBetween(Instant from, Instant to);
}
