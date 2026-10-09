package com.example.app.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "orders")
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;
  private String customer;
  private BigDecimal amount;
  private String status;
  private Instant createdAt;

  /** Asigna la fecha de creación justo antes de guardar el pedido. */
  @PrePersist
  public void prePersist() {
    this.createdAt = Instant.now();
  }

  /** Devuelve el identificador del pedido. */
  public Integer getId() { return id; }
  /** Actualiza el identificador del pedido. */
  public void setId(Integer id) { this.id = id; }
  /** Devuelve el nombre del cliente. */
  public String getCustomer() { return customer; }
  /** Actualiza el nombre del cliente. */
  public void setCustomer(String customer) { this.customer = customer; }
  /** Devuelve el importe del pedido. */
  public BigDecimal getAmount() { return amount; }
  /** Actualiza el importe del pedido. */
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  /** Devuelve el estado actual del pedido. */
  public String getStatus() { return status; }
  /** Actualiza el estado del pedido. */
  public void setStatus(String status) { this.status = status; }
  /** Devuelve la fecha de creación del pedido. */
  public Instant getCreatedAt() { return createdAt; }
  /** Actualiza la fecha de creación del pedido. */
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
