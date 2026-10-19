package com.example.app.dto;

import java.time.Instant;

/** Intervalo temporal solicitado para calcular el promedio de pedidos. */
public record OrderAverageRequest(Instant from, Instant to) {}
