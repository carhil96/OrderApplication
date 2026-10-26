package com.example.app.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Conversiones comunes para fechas que cruzan los límites de la aplicación. */
public final class DateTimeUtils {
  private DateTimeUtils() {}

  /** Convierte un instante a ISO-8601; conserva null cuando no hay fecha. */
  public static String toIsoString(Instant value) {
    return value == null ? null : value.toString();
  }

  /**
   * Convierte ISO-8601 a Instant. Si el texto no incluye zona horaria, lo interpreta
   * como UTC, que es el formato que puede producir PostgreSQL para TIMESTAMP.
   */
  public static Instant toInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException ignored) {
      return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    }
  }
}
