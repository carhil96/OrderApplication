package com.example.app.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "orders")
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_customer"))
  private Customer customerRecord;
  private BigDecimal amount;
  private String status;
  private Instant createdAt;
  private Instant statusOrder;

  @Version
  @Column(nullable = false)
  private Long version;

  /** Asigna la fecha de creación justo antes de guardar el pedido. */
  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.statusOrder = now;
  }

  /** Devuelve el identificador del pedido. */
  public Integer getId() { return id; }
  /** Actualiza el identificador del pedido. */
  public void setId(Integer id) { this.id = id; }
  /** Devuelve el nombre del cliente. */
  public String getCustomer() { return customerRecord == null ? null : customerRecord.getName(); }
  /** Mantiene el contrato de entrada; el servicio sustituye esta referencia provisional por un Customer persistido. */
  public void setCustomer(String customer) { this.customerRecord = customer == null ? null : new Customer(customer); }
  @JsonIgnore
  public Customer getCustomerRecord() { return customerRecord; }
  public void setCustomerRecord(Customer customerRecord) { this.customerRecord = customerRecord; }
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
  public Instant getStatusOrder() { return statusOrder; }
  public void setStatusOrder(Instant statusOrder) { this.statusOrder = statusOrder; }
  @JsonIgnore
  public Long getVersion() { return version; }
}
