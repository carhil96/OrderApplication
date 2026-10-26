package com.example.app.repository;

import com.example.app.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {}
