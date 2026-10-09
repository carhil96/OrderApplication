package com.example.app.repository;

import com.example.app.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Proporciona las operaciones CRUD de pedidos mediante Spring Data JPA. */
@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {}
