package com.example.app.service;

import com.example.app.dto.OrderEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickHouseWriterTest {

    @Test
    void usesStableOrderIdAsTheClickHouseInsertDeduplicationToken() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        Statement ddlStatement = mock(Statement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.createStatement()).thenReturn(ddlStatement);

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);

            ClickHouseWriter writer = new ClickHouseWriter();
            writer.persist(event(42));
            writer.persist(event(42));

            verify(connection, times(2)).prepareStatement(org.mockito.ArgumentMatchers.argThat(
                    sql -> sql.contains("insert_deduplicate = 1")
                            && sql.contains("insert_deduplication_token")
                            && sql.contains("order-42")));
            verify(ddlStatement).execute(org.mockito.ArgumentMatchers.contains(
                    "non_replicated_deduplication_window = 10000"));
            verify(statement, times(2)).setInt(1, 42);
            verify(statement, times(2)).setString(4, "CREATED");
            verify(statement, times(2)).executeUpdate();
        }
    }

    private OrderEvent event(int id) {
        return new OrderEvent(id, "Ada", new BigDecimal("10.50"), "CREATED", Instant.parse("2026-10-15T12:00:00Z"));
    }
}
