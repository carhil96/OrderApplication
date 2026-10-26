package com.example.app.graphql;

import com.example.app.domain.Customer;
import com.example.app.domain.Order;
import com.example.app.service.CustomerService;
import com.example.app.service.OrderService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

/** Operaciones GraphQL de Customer y resolución por lotes de Customer.orders. */
@Controller
public class CustomerResolver {
  private static final String ORDERS_BY_CUSTOMER = "ordersByCustomer";

  private final CustomerService customers;
  private final OrderService orders;

  public CustomerResolver(CustomerService customers, OrderService orders, BatchLoaderRegistry registry) {
    this.customers = customers;
    this.orders = orders;
    registry.<Long, List<Order>>forName(ORDERS_BY_CUSTOMER)
        .registerMappedBatchLoader((customerIds, environment) ->
            Mono.fromSupplier(() -> orders.findOrdersByCustomerIds(customerIds)));
  }

  @QueryMapping
  @PreAuthorize("hasAuthority('ORDER_READ')")
  public List<Customer> customers() {
    return customers.findAll();
  }

  @QueryMapping
  @PreAuthorize("hasAuthority('ORDER_READ')")
  public Customer customerById(@Argument Long id) {
    return customers.findById(id);
  }

  /** Encola los pedidos del cliente; DataLoader agrupa las claves por petición GraphQL. */
  @SchemaMapping(typeName = "Customer", field = "orders")
  @PreAuthorize("hasAuthority('ORDER_READ')")
  public CompletableFuture<List<Order>> orders(Customer customer,
      DataLoader<Long, List<Order>> ordersByCustomer) {
    return ordersByCustomer.load(customer.getId());
  }

  @MutationMapping
  @PreAuthorize("hasAuthority('ORDER_WRITE')")
  public Customer createCustomer(@Argument String name) {
    return customers.create(name);
  }

  @MutationMapping
  @PreAuthorize("hasAuthority('ORDER_WRITE')")
  public Customer updateCustomerName(@Argument Long id, @Argument String name) {
    return customers.rename(id, name);
  }

  @MutationMapping
  @PreAuthorize("hasAuthority('ORDER_WRITE')")
  public boolean deleteCustomer(@Argument Long id) {
    customers.delete(id);
    return true;
  }
}
