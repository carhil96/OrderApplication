package com.example.app.repository;

import com.example.app.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
  Optional<Customer> findByName(String name);
  boolean existsByNameAndIdNot(String name, Long id);
}
