package com.example.app.dto;

import java.math.BigDecimal;

public class OrderEvent {
  private Integer id;
  private String customer;
  private BigDecimal amount;

  public OrderEvent() {}

  public OrderEvent(Integer id, String customer, BigDecimal amount) {
    this.id = id;
    this.customer = customer;
    this.amount = amount;
  }

  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }
  public String getCustomer() { return customer; }
  public void setCustomer(String customer) { this.customer = customer; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
}
