package com.tpi.orders.dtos;

import java.math.BigDecimal;
import java.time.Instant;

/** Detalle de una operación ejecutada dentro del emparejamiento. */
public record TradeDTO(
        Long compradorId,
        Long vendedorId,
        String simbolo,
        Integer cantidad,
        BigDecimal precioUnitarioArs,
        BigDecimal totalArs,
        Long ordenVentaId,
        Instant fecha
) {}
