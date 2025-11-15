package com.example.app.graphql;

import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import com.example.app.domain.Order;
import com.example.app.repository.OrderRepository;
import com.example.app.service.OrderService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * GraphQL Resolver para operaciones de Order
 * Compatible con graphql-spring-boot-starter 11.1.0
 */
@Component
public class OrderResolver implements GraphQLQueryResolver, GraphQLMutationResolver {

  private final OrderRepository repo;
  private final OrderService service;

  public OrderResolver(OrderRepository repo, OrderService service) {
    this.repo = repo;
    this.service = service;
  }

    public List<Order> orders() {
      return repo.findAll();
    }

    public Order orderById(Integer id) {
      return repo.findById(id).orElse(null);
    }

  public Order createOrder(String customer, BigDecimal amount) {
    Order o = new Order();
    o.setCustomer(customer);
    o.setAmount(amount);
    return service.createOrder(o);
  }
}
