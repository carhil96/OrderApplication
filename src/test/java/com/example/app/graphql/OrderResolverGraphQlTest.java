package com.example.app.graphql;

import com.example.app.domain.Order;
import com.example.app.repository.OrderRepository;
import com.example.app.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@GraphQlTest(controllers = OrderResolver.class)
class OrderResolverGraphQlTest {

  @Autowired
  private GraphQlTester graphQlTester;

  @MockitoBean
  private OrderRepository repository;

  @MockitoBean
  private OrderService orderService;

  @Test
  void returnsOrdersThroughTheExistingQueryContract() {
    Order order = order("Ada", new BigDecimal("10.50"));
    when(repository.findAll()).thenReturn(List.of(order));

    graphQlTester.document("{ orders { customer amount status } }")
        .execute()
        .path("orders[0].customer").matchesJson("\"Ada\"")
        .path("orders[0].status").matchesJson("\"CREATED\"");
  }

  @Test
  void createsOrdersThroughTheExistingMutationContract() {
    when(orderService.createOrder(any(Order.class)))
        .thenAnswer(invocation -> {
          Order order = invocation.getArgument(0);
          order.setStatus("CREATED");
          return order;
        });

    graphQlTester.document("mutation { createOrder(customer: \"Ada\", amount: 10.50) { customer amount status } }")
        .execute()
        .path("createOrder.customer").matchesJson("\"Ada\"")
        .path("createOrder.status").matchesJson("\"CREATED\"");
  }

  private Order order(String customer, BigDecimal amount) {
    Order order = new Order();
    order.setCustomer(customer);
    order.setAmount(amount);
    order.setStatus("CREATED");
    return order;
  }
}
