package com.example.app.service;

import com.example.app.dto.OrderEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "clickhouse.integration", matches = "true")
class ClickHouseWriterIntegrationTest {
    private static final String TABLE = "default.orders_deduplication_it_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getProperty("clickhouse.test.url",
            "jdbc:clickhouse://localhost:8123/default");

    @BeforeEach
    void createDisposableTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
            statement.execute("CREATE TABLE " + TABLE + " ("
                    + "id UInt64, customer String, amount Float64, status String, created_at DateTime"
                    + ") ENGINE = MergeTree() ORDER BY (id, created_at) "
                    + "SETTINGS non_replicated_deduplication_window = 10000");
        }
    }

    @AfterEach
    void dropDisposableTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
        }
    }

    @Test
    void duplicateKafkaDeliveryProducesOnlyOneClickHouseRow() throws Exception {
        ClickHouseWriter writer = new ClickHouseWriter(URL, TABLE);
        OrderEvent event = new OrderEvent(777, "TDD", new BigDecimal("12.50"), "CREATED",
                Instant.parse("2026-10-15T12:00:00Z"));

        writer.persist(event);
        writer.persist(event);

        try (Connection connection = DriverManager.getConnection(URL);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count() FROM " + TABLE + " WHERE id = 777")) {
            result.next();
            assertEquals(1L, result.getLong(1));
        }
    }
}
