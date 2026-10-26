package com.example.app.controller;

import com.example.app.domain.Order;
import com.example.app.dto.OrderAverageRequest;
import com.example.app.dto.OrderAverageResponse;
import com.example.app.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class RestOrderController {
  private final OrderService orderService;

  /** Recibe los servicios que necesita el controlador para crear y consultar pedidos. */
  public RestOrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  /** Crea un pedido y devuelve el pedido guardado. */
  @PostMapping
  public ResponseEntity<Order> create(@RequestBody Order order) {
    Order saved = orderService.createOrder(order);
    return ResponseEntity.ok(saved);
  }

  /** Devuelve todos los pedidos guardados. */
  @GetMapping
  public List<Order> list() {
    return orderService.findAll();
  }

  /** Calcula el promedio de los importes en el intervalo [from, to). */
  @PostMapping("/average-amount")
  public OrderAverageResponse averageAmount(@RequestBody OrderAverageRequest request) {
    if (request.from() == null || request.to() == null || !request.from().isBefore(request.to())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "from and to are required and from must be before to");
    }
    return new OrderAverageResponse(orderService.averageAmountCreatedBetween(request.from(), request.to()));
  }
}
