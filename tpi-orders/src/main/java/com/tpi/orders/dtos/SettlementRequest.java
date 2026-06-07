package com.tpi.orders.dtos;

import java.math.BigDecimal;

/** Pedido de liquidación que se envía al microservicio de usuarios. */
public record SettlementRequest(
        Long compradorId,
        Long vendedorId,
        String simbolo,
        Integer cantidad,
        BigDecimal totalArs
) {}
