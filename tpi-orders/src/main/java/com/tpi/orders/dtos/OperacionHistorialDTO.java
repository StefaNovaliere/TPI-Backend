package com.tpi.orders.dtos;

import java.math.BigDecimal;

/** Operación que se registra en el microservicio de historial. */
public record OperacionHistorialDTO(
        String tipo,          // ORDEN_COMPRA, ORDEN_VENTA, TRADE
        Long usuarioId,
        Long contraparteId,   // null si no aplica
        String simbolo,
        Integer cantidad,
        BigDecimal precioUnitarioArs,
        BigDecimal totalArs,
        String estado
) {}
