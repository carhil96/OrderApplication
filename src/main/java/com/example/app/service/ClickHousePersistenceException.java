package com.example.app.service;

/** Indica un fallo al guardar un pedido en ClickHouse. */
public class ClickHousePersistenceException extends RuntimeException {
    public ClickHousePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
