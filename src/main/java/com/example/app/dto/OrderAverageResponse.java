package com.example.app.dto;

import java.math.BigDecimal;

/** Resultado del promedio; averageAmount es null cuando no hay pedidos. */
public record OrderAverageResponse(BigDecimal averageAmount) {}
