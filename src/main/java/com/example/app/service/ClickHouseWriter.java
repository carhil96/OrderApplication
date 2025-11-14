package com.example.app.service;

import com.example.app.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

@Component
public class ClickHouseWriter {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseWriter.class);

    private final List<Order> buffer = new LinkedList<>();

    /**
     * Encola una orden para ser insertada en ClickHouse.
     */
    public synchronized void enqueue(Order order) {
        buffer.add(order);
    }

    /**
     * Extrae y limpia el buffer de manera sincronizada.
     */
    private synchronized List<Order> drain() {
        List<Order> copy = new LinkedList<>(buffer);
        buffer.clear();
        return copy;
    }

    /**
     * Método programado para hacer flush del buffer cada 5s,
     * con inicial delay de 5s para asegurar que la app arranca correctamente.
     */
    @Scheduled(fixedRate = 5000, initialDelay = 5000)
    public void flush() {
        List<Order> batch = drain();
        if (batch.isEmpty()) {
            return;
        }

        String url = System.getenv().getOrDefault(
                "SPRING_CLICKHOUSE_URL",
                "jdbc:clickhouse://clickhouse:9000/default"
        );

        try (Connection conn = DriverManager.getConnection(url)) {
            String sql = "INSERT INTO orders (id, customer, amount, status, created_at) VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Order o : batch) {
                    ps.setLong(1, o.getId() != null ? o.getId() : 0L);
                    ps.setString(2, o.getCustomer() != null ? o.getCustomer() : "UNKNOWN");
                    ps.setDouble(3, o.getAmount() != null ? o.getAmount() : 0.0);
                    ps.setString(4, o.getStatus() != null ? o.getStatus() : "CREATED");
                    ps.setObject(5, o.getCreatedAt() != null ? o.getCreatedAt() : Instant.now());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            log.info("Flushed {} orders to ClickHouse", batch.size());

        } catch (Exception e) {
            // No lanzamos la excepción: simplemente loggeamos y el buffer ya está limpio
            log.error("Error writing to ClickHouse, orders lost: {}", batch.size(), e);
        }
    }
}
