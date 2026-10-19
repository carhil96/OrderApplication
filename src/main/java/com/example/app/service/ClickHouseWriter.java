package com.example.app.service;

import com.example.app.dto.OrderEvent;
import com.example.app.exception.ClickHousePersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

/** Persiste en ClickHouse una copia de cada pedido procesado desde Kafka. */
@Component
public class ClickHouseWriter {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseWriter.class);
    private static final String INSERT_SQL = "INSERT INTO %s "
            + "(id, customer, amount, status, created_at) "
            + "SETTINGS insert_deduplicate = 1, insert_deduplication_token = '%s' "
            + "VALUES (?, ?, ?, ?, ?)";
    private static final String ENABLE_DEDUPLICATION_SQL = "ALTER TABLE %s "
            + "MODIFY SETTING non_replicated_deduplication_window = 10000";
    private final String jdbcUrl;
    private final String tableName;
    private volatile boolean deduplicationEnabled;

    public ClickHouseWriter() {
        this(System.getenv().getOrDefault(
                        "SPRING_CLICKHOUSE_URL",
                        "jdbc:clickhouse://localhost:8123/default"),
                "default.orders");
    }

    ClickHouseWriter(String jdbcUrl, String tableName) {
        if (!tableName.matches("[A-Za-z0-9_]+\\.[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid ClickHouse table name: " + tableName);
        }
        this.jdbcUrl = jdbcUrl;
        this.tableName = tableName;
    }

    /** Inserta un evento con un token estable para reconocer sus reintentos. */
    public void persist(OrderEvent event) {
        String token = "order-" + event.getId();
        String sql = INSERT_SQL.formatted(tableName, token);

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            enableDeduplicationIfNeeded(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, event.getId());
                statement.setString(2, event.getCustomer());
                statement.setBigDecimal(3, event.getAmount());
                statement.setString(4, event.getStatus());
                statement.setTimestamp(5, Timestamp.from(
                        event.getCreatedAt() != null ? event.getCreatedAt() : Instant.now()));
                statement.executeUpdate();
                log.info("Persisted order {} to ClickHouse", event.getId());
            }
        } catch (SQLException e) {
            throw new ClickHousePersistenceException(
                    "Could not persist order " + event.getId() + " to ClickHouse", e);
        }
    }

    private void enableDeduplicationIfNeeded(Connection connection) throws SQLException {
        if (deduplicationEnabled) {
            return;
        }
        synchronized (this) {
            if (deduplicationEnabled) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(ENABLE_DEDUPLICATION_SQL.formatted(tableName));
            }
            deduplicationEnabled = true;
        }
    }
}
