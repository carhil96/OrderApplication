package com.example.app.exception;

/** Indica que un mensaje Kafka no contiene un pedido procesable. */
public class InvalidOrderEventException extends RuntimeException {
  public InvalidOrderEventException(String message) {
    super(message);
  }

  public InvalidOrderEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
