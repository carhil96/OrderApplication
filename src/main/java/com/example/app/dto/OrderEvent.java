package com.example.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderEvent {
  private Integer id;
  private String customer;
  private BigDecimal amount;
  private String status;
  private Instant createdAt;

  /** Constructor vacío usado al convertir el evento desde o hacia JSON. */
  public OrderEvent() {}

  /** Crea un evento con los datos principales del pedido. */
  public OrderEvent(Integer id, String customer, BigDecimal amount) {
    this(id, customer, amount, "CREATED", null);
  }

  /** Crea un evento con todos los datos necesarios para la copia analítica. */
  public OrderEvent(Integer id, String customer, BigDecimal amount, String status, Instant createdAt) {
    this.id = id;
    this.customer = customer;
    this.amount = amount;
    this.status = status;
    this.createdAt = createdAt;
  }

  /** Devuelve el identificador del pedido del evento. */
  public Integer getId() { return id; }
  /** Actualiza el identificador del pedido del evento. */
  public void setId(Integer id) { this.id = id; }
  /** Devuelve el nombre del cliente del evento. */
  public String getCustomer() { return customer; }
  /** Actualiza el nombre del cliente del evento. */
  public void setCustomer(String customer) { this.customer = customer; }
  /** Devuelve el importe del pedido del evento. */
  public BigDecimal getAmount() { return amount; }
  /** Actualiza el importe del pedido del evento. */
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  /** Devuelve el estado del pedido. */
  public String getStatus() { return status; }
  /** Actualiza el estado del pedido. */
  public void setStatus(String status) { this.status = status; }
  /** Devuelve la fecha de creación del pedido. */
  public Instant getCreatedAt() { return createdAt; }
  /** Actualiza la fecha de creación del pedido. */
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
