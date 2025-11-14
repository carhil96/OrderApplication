package com.example.app.controller;

import com.example.app.domain.Order;
import com.example.app.repository.OrderRepository;
import com.example.app.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class RestOrderController {
  private final OrderService orderService;
  private final OrderRepository repository;

  public RestOrderController(OrderService orderService, OrderRepository repository) {
    this.orderService = orderService;
    this.repository = repository;
  }

  @PostMapping
  public ResponseEntity<Order> create(@RequestBody Order order) {
    Order saved = orderService.createOrder(order);
    return ResponseEntity.ok(saved);
  }

  @GetMapping
  public List<Order> list() {
    return repository.findAll();
  }
}
