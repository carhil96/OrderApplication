package com.example.app.graphql;

import com.example.app.domain.Order;
import com.example.app.domain.Customer;
import com.example.app.service.CustomerService;
import com.example.app.service.OrderService;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(controllers = {OrderResolver.class, CustomerResolver.class})
class OrderResolverGraphQlTest {

  @Autowired
  private GraphQlTester graphQlTester;

  @MockitoBean
  private OrderService orderService;

  @MockitoBean
  private CustomerService customerService;

  /** Comprueba que la consulta GraphQL devuelve los pedidos existentes. */
  @Test
  void returnsOrdersThroughTheExistingQueryContract() {
    Order order = order("Ada", new BigDecimal("10.50"));
    when(orderService.findAll()).thenReturn(List.of(order));

    graphQlTester.document("{ orders { customer amount status } }")
        .execute()
        .path("orders[0].customer").matchesJson("\"Ada\"")
        .path("orders[0].status").matchesJson("\"CREATED\"");
  }

  /** Comprueba que la mutación GraphQL crea y devuelve un pedido. */
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

  @Test
  void batchesOrdersForAllCustomersInOneDataLoaderCall() {
    Customer first = customer(1L, "Ada");
    Customer second = customer(2L, "Grace");
    Customer third = customer(3L, "Katherine");
    when(customerService.findAll()).thenReturn(List.of(first, second, third));
    when(orderService.findOrdersByCustomerIds(Set.of(1L, 2L, 3L))).thenReturn(Map.of(
        1L, List.of(order("Ada", new BigDecimal("10.50"))),
        2L, List.of(order("Grace", new BigDecimal("20.00"))),
        3L, List.of()));

    graphQlTester.document("{ customers { id name orders { amount customer } } }")
        .execute()
        .path("customers[0].name").matchesJson("\"Ada\"")
        .path("customers[0].orders[0].customer").matchesJson("\"Ada\"")
        .path("customers[1].orders[0].amount").matchesJson("20.0")
        .path("customers[2].orders").matchesJson("[]");

    // Las tres relaciones anidadas se resuelven con una única llamada al servicio batch.
    verify(orderService).findOrdersByCustomerIds(Set.of(1L, 2L, 3L));
  }

  @Test
  void createsRenamesAndDeletesCustomersThroughGraphQlMutations() {
    Customer created = customer(3L, "Katherine");
    when(customerService.create("Katherine")).thenReturn(created);
    when(customerService.rename(3L, "Katherine Johnson")).thenAnswer(invocation -> {
      created.setName(invocation.getArgument(1));
      return created;
    });

    graphQlTester.document("mutation { createCustomer(name: \"Katherine\") { id name } }")
        .execute()
        .path("createCustomer.name").matchesJson("\"Katherine\"");
    graphQlTester.document("mutation { updateCustomerName(id: 3, name: \"Katherine Johnson\") { name } }")
        .execute()
        .path("updateCustomerName.name").matchesJson("\"Katherine Johnson\"");
    graphQlTester.document("mutation { deleteCustomer(id: 3) }")
        .execute()
        .path("deleteCustomer").entity(Boolean.class).isEqualTo(true);

    verify(customerService).delete(3L);
  }

  @Test
  void updatesOrderStatusThroughTheGraphQlEnumContract() {
    Order updated = order("Ada", new BigDecimal("10.50"));
    updated.setStatus("DELIVERED");
    updated.setCreatedAt(Instant.parse("2026-10-20T10:00:00Z"));
    updated.setStatusOrder(Instant.parse("2026-10-21T11:30:00Z"));
    when(orderService.updateStatus(42, "DELIVERED")).thenReturn(updated);

    graphQlTester.document("mutation { updateOrderStatus(id: 42, status: DELIVERED) { status createdAt statusOrder } }")
        .execute()
        .path("updateOrderStatus.status").matchesJson("\"DELIVERED\"")
        .path("updateOrderStatus.createdAt").matchesJson("\"2026-10-20T10:00:00Z\"")
        .path("updateOrderStatus.statusOrder").matchesJson("\"2026-10-21T11:30:00Z\"");
  }

  private Customer customer(Long id, String name) {
    Customer customer = new Customer(name);
    ReflectionTestUtils.setField(customer, "id", id);
    return customer;
  }

  /** Construye un pedido de ejemplo para las pruebas GraphQL. */
  private Order order(String customer, BigDecimal amount) {
    Order order = new Order();
    order.setCustomerRecord(new Customer(customer));
    order.setAmount(amount);
    order.setStatus("CREATED");
    return order;
  }
}
