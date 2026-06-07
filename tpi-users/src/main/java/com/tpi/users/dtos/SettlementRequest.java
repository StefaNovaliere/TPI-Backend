package com.tpi.users.dtos;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Pedido de liquidación de una operación de compra-venta. Lo envía el
 * microservicio de órdenes cuando empareja una compra con una venta.
 * La liquidación (debitar al comprador, acreditar al vendedor y mover las
 * acciones) se ejecuta de forma atómica (transacción) en este microservicio.
 */
public record SettlementRequest(
        @NotNull Long compradorId,
        @NotNull Long vendedorId,
        @NotNull String simbolo,
        @NotNull Integer cantidad,
        @NotNull BigDecimal totalArs
) {}
