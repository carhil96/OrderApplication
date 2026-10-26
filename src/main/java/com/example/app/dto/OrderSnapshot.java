package com.example.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSnapshot(Integer id, Long customerId, String customer, BigDecimal amount,
                            String status, Instant createdAt, Instant statusOrder,
                            long version) {}
