package com.example.app.dto;

import java.math.BigDecimal;

public class OrderEvent {
  private Integer id;
  private String customer;
  private BigDecimal amount;

  /** Constructor vacío usado al convertir el evento desde o hacia JSON. */
  public OrderEvent() {}

  /** Crea un evento con los datos principales del pedido. */
  public OrderEvent(Integer id, String customer, BigDecimal amount) {
    this.id = id;
    this.customer = customer;
    this.amount = amount;
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
}
