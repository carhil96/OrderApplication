package com.example.app.service;

import com.example.app.domain.Customer;
import com.example.app.domain.OutboxEvent;
import com.example.app.dto.CustomerSnapshot;
import com.example.app.dto.OutboxPayload;
import com.example.app.repository.CustomerRepository;
import com.example.app.repository.OrderRepository;
import com.example.app.repository.OutboxRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

@Service
public class CustomerService {
  private final CustomerRepository customers;
  private final OrderRepository orders;
  private final OutboxRepository outbox;
  private final JsonMapper mapper;
  private final EntityManager entityManager;

  public CustomerService(CustomerRepository customers, OrderRepository orders,
      OutboxRepository outbox, JsonMapper mapper, EntityManager entityManager) {
    this.customers = customers;
    this.orders = orders;
    this.outbox = outbox;
    this.mapper = mapper;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public List<Customer> findAll() {
    return customers.findAll();
  }

  @Transactional(readOnly = true)
  public Customer findById(Long id) {
    return customers.findById(id).orElse(null);
  }

  @Transactional
  public Customer create(String name) {
    validateName(name);
    if (customers.findByName(name).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "customer name already exists");
    }
    Customer customer = customers.save(new Customer(name));
    entityManager.flush();
    publish(customer, "CustomerCreated", customer.getVersion(), false);
    return customer;
  }

  @Transactional
  public Customer rename(Long id, String newName) {
    validateName(newName);
    Customer customer = customers.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found"));
    if (customers.existsByNameAndIdNot(newName, id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "customer name already exists");
    }
    customer.setName(newName);
    entityManager.flush();
    publish(customer, "CustomerRenamed", customer.getVersion(), false);
    return customer;
  }

  @Transactional
  public void delete(Long id) {
    Customer customer = customers.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found"));
    if (orders.existsByCustomerRecordId(id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "customer has orders and cannot be deleted");
    }
    publish(customer, "CustomerDeleted", customer.getVersion() + 1, true);
    customers.delete(customer);
  }

  private void publish(Customer customer, String eventType, long version, boolean deleted) {
    CustomerSnapshot snapshot = new CustomerSnapshot(customer.getId(), deleted ? null : customer.getName(),
        version, deleted);
    try {
      OutboxPayload payload = new OutboxPayload(eventType, customer.getId().toString(), version,
          Instant.now(), mapper.readTree(mapper.writeValueAsString(snapshot)));
      outbox.save(new OutboxEvent("customer", customer.getId().toString(), eventType, version,
          mapper.readTree(mapper.writeValueAsString(payload)), Instant.now()));
    } catch (Exception e) {
      throw new IllegalStateException("Could not serialize customer outbox event", e);
    }
  }

  private void validateName(String name) {
    if (name == null || name.isBlank() || name.length() > 200) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "customer name must contain non-whitespace text and be at most 200 characters");
    }
  }
}
