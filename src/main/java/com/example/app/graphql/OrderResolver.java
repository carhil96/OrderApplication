package com.example.app.graphql;

import com.example.app.domain.Order;
import com.example.app.repository.OrderRepository;
import com.example.app.service.OrderService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adaptador GraphQL para operaciones de Order.
 */
@Controller
public class OrderResolver {

  private final OrderRepository repo;
  private final OrderService service;

  public OrderResolver(OrderRepository repo, OrderService service) {
    this.repo = repo;
    this.service = service;
  }

  @QueryMapping
  public List<Order> orders() {
    return repo.findAll();
  }

  @QueryMapping
  public Order orderById(@Argument Integer id) {
    return repo.findById(id).orElse(null);
  }

  @MutationMapping
  public Order createOrder(@Argument String customer, @Argument BigDecimal amount) {
    Order o = new Order();
    o.setCustomer(customer);
    o.setAmount(amount);
    return service.createOrder(o);
  }
}
