package com.example.app.exception;

/** Indica un fallo al guardar un pedido en ClickHouse. */
public class ClickHousePersistenceException extends RuntimeException {
    public ClickHousePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
