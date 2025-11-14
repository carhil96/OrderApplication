package com.example.app.graphql;

import com.coxautodev.graphql.tools.GraphQLMutationResolver;
import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import com.example.app.domain.Order;
import com.example.app.repository.OrderRepository;
import com.example.app.service.OrderService;
import org.springframework.stereotype.Component;

import java.util.List;

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

  public Order orderById(Long id) {
    return repo.findById(id).orElse(null);
  }

  public Order createOrder(String customer, Double amount) {
    Order o = new Order();
    o.setCustomer(customer);
    o.setAmount(amount);
    return service.createOrder(o);
  }
}
