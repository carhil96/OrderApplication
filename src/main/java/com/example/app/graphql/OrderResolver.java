package com.example.app.graphql;

import com.example.app.domain.Order;
import com.example.app.service.OrderService;
import com.example.app.util.DateTimeUtils;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adaptador GraphQL para operaciones de Order.
 */
@Controller
public class OrderResolver {

  private final OrderService service;

  /** Recibe el repositorio y el servicio usados por las operaciones GraphQL. */
  public OrderResolver(OrderService service) {
    this.service = service;
  }

  /** Devuelve todos los pedidos para la consulta GraphQL `orders`. */
  @QueryMapping
  @PreAuthorize("hasAuthority('ORDER_READ')")
  public List<Order> orders() {
    return service.findAll();
  }

  /** Busca un pedido por identificador; devuelve null si no existe. */
  @QueryMapping
  @PreAuthorize("hasAuthority('ORDER_READ')")
  public Order orderById(@Argument Integer id) {
    return service.findById(id);
  }

  /** Crea un pedido con el cliente y el importe recibidos. */
  @MutationMapping
  @PreAuthorize("hasAuthority('ORDER_WRITE')")
  public Order createOrder(@Argument String customer, @Argument BigDecimal amount) {
    Order o = new Order();
    o.setCustomer(customer);
    o.setAmount(amount);
    return service.createOrder(o);
  }

  /** Cambia únicamente el estado según la transición permitida por el servicio. */
  @MutationMapping
  @PreAuthorize("hasAuthority('ORDER_WRITE')")
  public Order updateOrderStatus(@Argument Integer id, @Argument OrderStatus status) {
    return service.updateStatus(id, status.name());
  }

  /** Serializa el instante como texto, según el contrato SDL aprobado. */
  @SchemaMapping(typeName = "Order", field = "createdAt")
  public String createdAt(Order order) {
    return DateTimeUtils.toIsoString(order.getCreatedAt());
  }

  /** Serializa el instante como texto, según el contrato SDL aprobado. */
  @SchemaMapping(typeName = "Order", field = "statusOrder")
  public String statusOrder(Order order) {
    return DateTimeUtils.toIsoString(order.getStatusOrder());
  }
}
