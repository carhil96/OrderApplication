package com.example.app.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class Customer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200, unique = true)
  private String name;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Customer() {}

  public Customer(String name) { this.name = name; }
  public Long getId() { return id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public Long getVersion() { return version; }
}
