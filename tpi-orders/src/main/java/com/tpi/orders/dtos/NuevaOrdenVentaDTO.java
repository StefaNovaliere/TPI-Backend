package com.tpi.orders.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Pedido para registrar una orden de venta. */
public record NuevaOrdenVentaDTO(
        @NotNull Long usuarioId,
        @NotNull String simbolo,
        @NotNull @Positive Integer cantidad,
        @NotNull @Positive BigDecimal precioMinArs
) {}
